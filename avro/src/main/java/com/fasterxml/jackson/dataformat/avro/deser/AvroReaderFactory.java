package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.io.ResolvingDecoder;

import com.fasterxml.jackson.dataformat.avro.deser.AvroScalarReader.*;

/**
 * Helper class used for constructing a hierarchic reader for given
 * (reader-) schema.
 */
public class AvroReaderFactory
{
    protected final static AvroScalarReader READER_BOOLEAN = new BooleanReader();
    protected final static AvroScalarReader READER_BYTES = new BytesReader();
    protected final static AvroScalarReader READER_DOUBLE = new DoubleReader();
    protected final static AvroScalarReader READER_FLOAT = new FloatReader();
    protected final static AvroScalarReader READER_INT = new IntReader();
    protected final static AvroScalarReader READER_LONG = new LongReader();
    protected final static AvroScalarReader READER_NULL = new NullReader();
    protected final static AvroScalarReader READER_STRING = new StringReader();

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
     * @param decoder 
     */
    public AvroStructureReader createReader(Schema schema, ResolvingDecoder decoder)
    {
        AvroStructureReader reader = _knownReaders.get(_typeName(schema));
        if (reader != null) {
            return reader;
        }
        switch (schema.getType()) {
        case ARRAY:
            return createArrayReader(schema, decoder);
        case MAP: 
            return createMapReader(schema, decoder);
        case RECORD:
            return createRecordReader(schema, decoder);
        case UNION:
            return createUnionReader(schema, decoder);
        default:
            // for other types, we need wrappers
            return new ScalarReaderWrapper(createDecoder(schema));
        }
    }

    public AvroScalarReader createDecoder(Schema type)
    {
        switch (type.getType()) {
        case BOOLEAN:
            return READER_BOOLEAN;
        case BYTES: 
            return READER_BYTES;
        case DOUBLE: 
            return READER_DOUBLE;
        case ENUM: 
            return new EnumDecoder(type);
        case FIXED: 
            return new FixedDecoder(type);
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
    
    private AvroStructureReader createArrayReader(Schema schema, ResolvingDecoder decoder)
    {
        Schema elementType = schema.getElementType();
        AvroScalarReader scalar = createDecoder(elementType);
        if (scalar != null) {
            return ArrayReader.scalar(scalar);
        }
        return ArrayReader.nonScalar(createReader(elementType, decoder));
    }

    private AvroStructureReader createMapReader(Schema schema, ResolvingDecoder decoder)
    {
        Schema elementType = schema.getValueType();
        AvroScalarReader dec = createDecoder(elementType);
        if (dec != null) {
            return new MapReader(dec);
        }
        return new MapReader(createReader(elementType, decoder));
    }

    private AvroStructureReader createRecordReader(Schema schema, ResolvingDecoder decoder)
    {
        List<Schema.Field> fields;
		try {
			fields = Arrays.asList(decoder.readFieldOrder());
		} catch (IOException e) {
			throw new IllegalStateException("I say, I say, I can't read this son", e);
		}
        AvroFieldWrapper[] fieldReaders = new AvroFieldWrapper[fields.size()];
        RecordReader reader = new RecordReader(fieldReaders);
        _knownReaders.put(_typeName(schema), reader);
        int i = 0;
        for (Schema.Field field : fields) {
            fieldReaders[i++] = createFieldReader(field, decoder);
        }
        return reader;
    }

    private AvroStructureReader createUnionReader(Schema schema, ResolvingDecoder decoder)
    {
        final List<Schema> types = schema.getTypes();
        AvroStructureReader[] typeReaders = new AvroStructureReader[types.size()];
        int i = 0;
        for (Schema type : types) {
            typeReaders[i++] = createReader(type, decoder);
        }
        return new UnionReader(typeReaders);
    }

    private AvroFieldWrapper createFieldReader(Schema.Field field, ResolvingDecoder decoder) {
        return createFieldReader(field.name(), field.schema(), decoder);
    }

    private AvroFieldWrapper createFieldReader(String name, Schema type, ResolvingDecoder decoder)
    {
        AvroScalarReader scalar = createDecoder(type);
        if (scalar != null) {
            return new AvroFieldWrapper(name, scalar);
        }
        return new AvroFieldWrapper(name, createReader(type, decoder));
    }

    private String _typeName(Schema schema) {
        return schema.getFullName();
    }
}
