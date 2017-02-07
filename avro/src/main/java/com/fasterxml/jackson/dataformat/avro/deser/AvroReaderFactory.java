package com.fasterxml.jackson.dataformat.avro.deser;

import java.util.*;

import org.apache.avro.Schema;

import com.fasterxml.jackson.dataformat.avro.deser.ScalarDecoder.*;

/**
 * Helper class used for constructing a hierarchic reader for given
 * (reader-) schema.
 */
public abstract class AvroReaderFactory
{
    protected final static ScalarDecoder READER_BOOLEAN = new BooleanReader();
    protected final static ScalarDecoder READER_BYTES = new BytesReader();
    protected final static ScalarDecoder READER_DOUBLE = new DoubleReader();
    protected final static ScalarDecoder READER_FLOAT = new FloatReader();
    protected final static ScalarDecoder READER_INT = new IntReader();
    protected final static ScalarDecoder READER_LONG = new LongReader();
    protected final static ScalarDecoder READER_NULL = new NullReader();
    protected final static ScalarDecoder READER_STRING = new StringReader();

    /**
     * To resolve cyclic types, need to keep track of resolved named
     * types.
     */
    protected final TreeMap<String, AvroStructureReader> _knownReaders
        = new TreeMap<String, AvroStructureReader>();

    /*
    /**********************************************************************
    /* Public API: root methods to create reader
    /**********************************************************************
     */

    public static AvroStructureReader createFor(Schema schema) {
        return new NonResolving().createReader(schema);
    }

    public static AvroStructureReader createFor(Schema writerSchema,
            Schema readerSchema) {
        return new Resolving().createReader(writerSchema, readerSchema);
    }

    /*
    /**********************************************************************
    /* Public API: factory methods
    /**********************************************************************
     */

    public ScalarDecoder createDecoder(Schema type)
    {
        switch (type.getType()) {
        case BOOLEAN:
            return READER_BOOLEAN;
        case BYTES: 
            return READER_BYTES;
        case DOUBLE: 
            return READER_DOUBLE;
        case ENUM: 
            return new EnumDecoder(type.getFullName(), type.getEnumSymbols());
        case FIXED: 
            return new FixedDecoder(type.getFixedSize());
        case FLOAT: 
            return READER_FLOAT;
        case INT:
            return READER_INT;
        case LONG: 
            return READER_LONG;
        case NULL: 
            return READER_NULL;
        case STRING: 
            return READER_STRING;
        case UNION:
            /* Union is a "scalar union" if all the alternative types
             * are scalar. One common type is that of "nullable" one,
             * but general handling should work just fine.
             */
            List<Schema> types = type.getTypes();
            {
                ScalarDecoder[] readers = new ScalarDecoder[types.size()];
                int i = 0;
                for (Schema schema : types) {
                    ScalarDecoder reader = createDecoder(schema);
                    if (reader == null) { // non-scalar; no go
                        return null;
                    }
                    readers[i++] = reader;
                }
                return new ScalarUnionReader(readers);
            }
        case ARRAY: // ok to call just can't handle
        case MAP:
        case RECORD:
            return null;
        }
        // but others are not recognized
        throw new IllegalStateException("Unrecognized Avro Schema type: "+type.getType());
    }

    /*
    /**********************************************************************
    /* Factory methods for non-resolving cases, shared by sub-classes
    /**********************************************************************
     */

    /**
     * Method for creating a reader instance for specified type,
     * only using specific schema that was used to encoded data
     * ("writer schema").
     */
    public AvroStructureReader createReader(Schema schema)
    {
        AvroStructureReader reader = _knownReaders.get(_typeName(schema));
        if (reader != null) {
            return reader;
        }
        switch (schema.getType()) {
        case ARRAY:
            return createArrayReader(schema);
        case MAP: 
            return createMapReader(schema);
        case RECORD:
            return createRecordReader(schema);
        case UNION:
            return createUnionReader(schema);
        default:
            // for other types, we need wrappers
            return new ScalarDecoderWrapper(createDecoder(schema));
        }
    }

    protected AvroStructureReader createArrayReader(Schema schema)
    {
        Schema elementType = schema.getElementType();
        ScalarDecoder scalar = createDecoder(elementType);
        if (scalar != null) {
            return ArrayReader.scalar(scalar);
        }
        return ArrayReader.nonScalar(createReader(elementType));
    }

    protected AvroStructureReader createMapReader(Schema schema)
    {
        Schema elementType = schema.getValueType();
        ScalarDecoder dec = createDecoder(elementType);
        if (dec != null) {
            return new MapReader(dec);
        }
        return new MapReader(createReader(elementType));
    }

    protected AvroStructureReader createRecordReader(Schema schema)
    {
        final List<Schema.Field> fields = schema.getFields();
        AvroFieldWrapper[] fieldReaders = new AvroFieldWrapper[fields.size()];
        RecordReader reader = new RecordReader.Std(fieldReaders);
        _knownReaders.put(_typeName(schema), reader);
        int i = 0;
        for (Schema.Field field : fields) {
            fieldReaders[i++] = createFieldReader(field);
        }
        return reader;
    }

    protected AvroStructureReader createUnionReader(Schema schema)
    {
        final List<Schema> types = schema.getTypes();
        AvroStructureReader[] typeReaders = new AvroStructureReader[types.size()];
        int i = 0;
        for (Schema type : types) {
            typeReaders[i++] = createReader(type);
        }
        return new UnionReader(typeReaders);
    }

    protected AvroFieldWrapper createFieldReader(Schema.Field field) {
        return createFieldReader(field.name(), field.schema());
    }

    protected AvroFieldWrapper createFieldReader(String name, Schema type)
    {
        ScalarDecoder scalar = createDecoder(type);
        if (scalar != null) {
            return AvroFieldWrapper.construct(name, scalar);
        }
        return AvroFieldWrapper.construct(name, createReader(type));
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected final String _typeName(Schema schema) {
        return schema.getFullName();
    }

    /*
    /**********************************************************************
    /* Implementations
    /**********************************************************************
     */

    /**
     * Implementation used when no schema-resolution is needed, when we are using
     * same schema for reading as was used for writing.
     *
     */
    private static class NonResolving extends AvroReaderFactory
    {
        protected NonResolving() { }
    }

    /**
     * Implementation used when schema-resolution is needed, when we are using
     * different schema for reading ("reader schema") than was used for
     * writing encoded data ("writer schema")
     */
    private static class Resolving extends AvroReaderFactory
    {
        protected Resolving() { }

        /**
         * Method for creating a reader instance for specified type.
         */
        public AvroStructureReader createReader(Schema writerSchema, Schema readerSchema)
        {
            // NOTE: it is assumed writer-schema has been modified with aliases so
            //   that the names are same, so we could use either name:
            AvroStructureReader reader = _knownReaders.get(_typeName(readerSchema));
            if (reader != null) {
                return reader;
            }
            // but the type to decode must be writer-schema indicated one (but
            // also same as or promotable to reader-schema one)
            switch (writerSchema.getType()) {
            case ARRAY:
                return createArrayReader(writerSchema, readerSchema);
            case MAP: 
                return createMapReader(writerSchema, readerSchema);
            case RECORD:
                return createRecordReader(writerSchema, readerSchema);
            case UNION:
                return createUnionReader(writerSchema, readerSchema);
            default:
                // for other types, we need wrappers
                return new ScalarDecoderWrapper(createDecoder(writerSchema));
            }
        }

        protected AvroStructureReader createArrayReader(Schema writerSchema, Schema readerSchema)
        {
            Schema elementType = writerSchema.getElementType();
            ScalarDecoder scalar = createDecoder(elementType);
            if (scalar != null) {
                return ArrayReader.scalar(scalar);
            }
            return ArrayReader.nonScalar(createReader(elementType,
                    readerSchema.getElementType()));
        }

        protected AvroStructureReader createMapReader(Schema writerSchema, Schema readerSchema)
        {
            Schema elementType = writerSchema.getValueType();
            ScalarDecoder dec = createDecoder(elementType);
            if (dec != null) {
                return new MapReader(dec);
            }
            return new MapReader(createReader(elementType,
                    readerSchema.getElementType()));
        }

        protected AvroStructureReader createRecordReader(Schema writerSchema, Schema readerSchema)
        {
            // Ok, this gets bit more complicated: need to iterate over writer schema
            // (since that will be the order fields are decoded in!), but also
            // keep track of which reader fields are being produced.

            final List<Schema.Field> writerFields = writerSchema.getFields();
            Map<String,Schema.Field> readerFields = _fieldMap(readerSchema.getFields());

            // note: despite changes, we will always have known number of field entities,
            // ones from writer schema -- some may skip, but there's entry there
            AvroFieldWrapper[] fieldReaders = new AvroFieldWrapper[writerFields.size()];
            RecordReader reader = new RecordReader.Resolving(fieldReaders);

            // as per earlier, names should be the same
            _knownReaders.put(_typeName(readerSchema), reader);
            int i = 0;
            for (Schema.Field writerField : writerFields) {
                Schema.Field readerField = readerFields.remove(writerField.name());
                // need a skipper:
                fieldReaders[i++] = (readerField == null)
                        ? createFieldSkipper(writerField.name(),
                                writerField.schema())
                        : createFieldReader(readerField.name(),
                                writerField.schema(), readerField.schema());
            }
            return reader;
        }

        private Map<String,Schema.Field> _fieldMap(List<Schema.Field> fields)
        {
            Map<String,Schema.Field> result = new HashMap<String,Schema.Field>();
            for (Schema.Field field : fields) {
                result.put(field.name(), field);
            }
            return result;
        }

        protected AvroStructureReader createUnionReader(Schema writerSchema, Schema readerSchema)
        {
            final List<Schema> types = writerSchema.getTypes();
            AvroStructureReader[] typeReaders = new AvroStructureReader[types.size()];
            int i = 0;
            for (Schema type : types) {
                typeReaders[i++] = createReader(type);
            }
            return new UnionReader(typeReaders);
        }

        protected AvroFieldWrapper createFieldReader(String name,
                Schema writerSchema, Schema readerSchema)
        {
            ScalarDecoder scalar = createDecoder(writerSchema);
            if (scalar != null) {
                return AvroFieldWrapper.construct(name, scalar);
            }
            return AvroFieldWrapper.construct(name,
                    createReader(writerSchema, readerSchema));
        }

        protected AvroFieldWrapper createFieldSkipper(String name,
                Schema writerSchema)
        {
            // 05-Feb-2016, tatu: initially simply construct regular
            ScalarDecoder scalar = createDecoder(writerSchema);
            if (scalar != null) {
                return AvroFieldWrapper.constructSkipper(name, scalar);
            }
            return AvroFieldWrapper.constructSkipper(name,
                    createReader(writerSchema));
        }
    }
}
