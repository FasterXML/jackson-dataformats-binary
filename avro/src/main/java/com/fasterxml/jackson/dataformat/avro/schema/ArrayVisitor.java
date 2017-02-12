package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator.Feature;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.specific.SpecificData;

public class ArrayVisitor
    extends JsonArrayFormatVisitor.Base
    implements SchemaBuilder
{
    private static final Schema INT_CHAR_SCHEMA = Schema.create(Type.INT);

    static {
        INT_CHAR_SCHEMA.addProp(SpecificData.CLASS_PROP, Character.class.getName());
    }

    protected final JavaType _type;
    
    protected final DefinedSchemas _schemas;

    protected Schema _elementSchema;
    
    public ArrayVisitor(SerializerProvider p, JavaType type, DefinedSchemas schemas)
    {
        super(p);
        _type = type;
        _schemas = schemas;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_elementSchema == null) {
            throw new IllegalStateException("No element schema created for: "+_type);
        }
        Schema schema = Schema.createArray(_elementSchema);
        if (_type.isArrayType()) {
            schema.addProp(SpecificData.CLASS_PROP, _type.toCanonical());
        }
        return schema;
    }

    /*
    /**********************************************************
    /* JsonArrayFormatVisitor implementation
    /**********************************************************
     */

    @Override
    public void itemsFormat(JsonFormatVisitable visitable, JavaType type)
            throws JsonMappingException
    {
        if (type.hasRawClass(Character.class) && !isWritingCharArraysAsStrings()) {
            _elementSchema = INT_CHAR_SCHEMA;
            return;
        }

        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        visitable.acceptJsonFormatVisitor(wrapper, type);
        _elementSchema = wrapper.getAvroSchema();
    }

    @Override
    public void itemsFormat(JsonFormatTypes type) throws JsonMappingException
    {
        /*
         * Unlike Jackson, Avro treats character arrays as an array of ints with the java.lang.Character class type. Unless this has been
         * turned off in the generator, we need to handle this specially.
         */
        if (_type.hasRawClass(char[].class) && !isWritingCharArraysAsStrings()) {
            _elementSchema = INT_CHAR_SCHEMA;
            return;

        }
        _elementSchema = AvroSchemaHelper.simpleSchema(type, _type.getContentType());
    }

    protected boolean isWritingCharArraysAsStrings() {
        if (_provider.getGenerator() instanceof AvroGenerator) {
            return ((AvroGenerator) _provider.getGenerator()).isEnabled(Feature.WRITE_CHAR_ARRAY_AS_STRING);
        }
        return Feature.WRITE_CHAR_ARRAY_AS_STRING.enabledByDefault();
    }
}
