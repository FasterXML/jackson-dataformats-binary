package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;

import static com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS;

public class ArrayVisitor
    extends JsonArrayFormatVisitor.Base
    implements SchemaBuilder
{
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
        if (!_type.hasRawClass(List.class)) {
            schema.addProp(AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_type));
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
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        visitable.acceptJsonFormatVisitor(wrapper, type);
        _elementSchema = wrapper.getAvroSchema();
    }

    @Override
    public void itemsFormat(JsonFormatTypes type) throws JsonMappingException
    {
        // Unlike Jackson, Avro treats character arrays as an array of ints with the java.lang.Character class type.
        if (_type.hasRawClass(char[].class)) {
            _elementSchema = AvroSchemaHelper.typedSchema(Type.INT, _type.getContentType());
        } else {
            _elementSchema = AvroSchemaHelper.simpleSchema(type, _type.getContentType());
        }
    }
}
