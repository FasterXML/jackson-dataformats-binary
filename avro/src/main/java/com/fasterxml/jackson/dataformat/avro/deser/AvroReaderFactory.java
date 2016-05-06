package com.fasterxml.jackson.dataformat.avro.deser;

import java.util.*;

import org.apache.avro.Schema;

import com.fasterxml.jackson.dataformat.avro.deser.AvroScalarReader.*;

public class AvroReaderFactory
{
    protected final static AvroScalarReader DECODER_BOOLEAN = new BooleanReader();
    protected final static AvroScalarReader DECODER_BYTES = new BytesReader();
    protected final static AvroScalarReader DECODER_DOUBLE = new DoubleReader();
    protected final static AvroScalarReader DECODER_FLOAT = new FloatReader();
    protected final static AvroScalarReader DECODER_INT = new IntReader();
    protected final static AvroScalarReader DECODER_LONG = new LongReader();
    protected final static AvroScalarReader DECODER_NULL = new NullReader();
    protected final static AvroScalarReader DECODER_STRING = new StringReader();

    /**
     * To resolve cyclic types, need to keep track of resolved named
     * types.
     */
    protected final TreeMap<String, AvroStructureReader> _knownReaders
        = new TreeMap<String, AvroStructureReader>();
    
    /*
    /**********************************************************************
    /* Public API: factory methods
    /**********************************************************************
     */
    
    /**
     * Method for creating a reader instance for specified type.
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
            return new ScalarReaderWrapper(createDecoder(schema));
        }
    }

    public AvroScalarReader createDecoder(Schema type)
    {
        switch (type.getType()) {
        case BOOLEAN:
            return DECODER_BOOLEAN;
        case BYTES: 
            return DECODER_BYTES;
        case DOUBLE: 
            return DECODER_DOUBLE;
        case ENUM: 
            return new EnumDecoder(type);
        case FIXED: 
            return new FixedDecoder(type);
        case FLOAT: 
            return DECODER_FLOAT;
        case INT:
            return DECODER_INT;
        case LONG: 
            return DECODER_LONG;
        case NULL: 
            return DECODER_NULL;
        case STRING: 
            return DECODER_STRING;
        case UNION:
            /* Union is a "scalar union" if all the alternative types
             * are scalar. One common type is that of "nullable" one,
             * but general handling should work just fine.
             */
            List<Schema> types = type.getTypes();
            {
                AvroScalarReader[] readers = new AvroScalarReader[types.size()];
                int i = 0;
                for (Schema schema : types) {
                    AvroScalarReader reader = createDecoder(schema);
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
    /* Internal methods
    /**********************************************************************
     */
    
    private AvroStructureReader createArrayReader(Schema schema)
    {
        Schema elementType = schema.getElementType();
        AvroScalarReader scalar = createDecoder(elementType);
        if (scalar != null) {
            return ArrayReader.scalar(scalar);
        }
        return ArrayReader.nonScalar(createReader(elementType));
    }

    private AvroStructureReader createMapReader(Schema schema)
    {
        Schema elementType = schema.getValueType();
        AvroScalarReader dec = createDecoder(elementType);
        if (dec != null) {
            return new MapReader(dec);
        }
        return new MapReader(createReader(elementType));
    }

    private AvroStructureReader createRecordReader(Schema schema)
    {
        final List<Schema.Field> fields = schema.getFields();
        AvroFieldWrapper[] fieldReaders = new AvroFieldWrapper[fields.size()];
        RecordReader reader = new RecordReader(fieldReaders);
        _knownReaders.put(_typeName(schema), reader);
        int i = 0;
        for (Schema.Field field : fields) {
            fieldReaders[i++] = createFieldReader(field);
        }
        return reader;
    }

    private AvroStructureReader createUnionReader(Schema schema)
    {
        final List<Schema> types = schema.getTypes();
        AvroStructureReader[] typeReaders = new AvroStructureReader[types.size()];
        int i = 0;
        for (Schema type : types) {
            typeReaders[i++] = createReader(type);
        }
        return new UnionReader(typeReaders);
    }

    private AvroFieldWrapper createFieldReader(Schema.Field field) {
        return createFieldReader(field.name(), field.schema());
    }

    private AvroFieldWrapper createFieldReader(String name, Schema type)
    {
        AvroScalarReader scalar = createDecoder(type);
        if (scalar != null) {
            return new AvroFieldWrapper(name, scalar);
        }
        return new AvroFieldWrapper(name, createReader(type));
    }

    private String _typeName(Schema schema) {
        return schema.getFullName();
    }
}
