package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;

public class MapVisitor extends JsonMapFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;
    
    protected final DefinedSchemas _schemas;
    
    protected Schema _valueSchema;
    
    public MapVisitor(SerializerProvider p, JavaType type, DefinedSchemas schemas)
    {
        super(p);
        _type = type;
        _schemas = schemas;
    }

    @Override
    public Schema builtAvroSchema() {
        // Assumption now is that we are done, so let's assign fields
        if (_valueSchema == null) {
            throw new IllegalStateException("Missing value type for "+_type);
        }
        return Schema.createMap(_valueSchema);
    }

    /*
    /**********************************************************
    /* JsonMapFormatVisitor implementation
    /**********************************************************
     */
    
    @Override
    public void keyFormat(JsonFormatVisitable handler, JavaType keyType)
        throws JsonMappingException
    {
        /* We actually don't care here, since Avro only has String-keyed
         * Maps like JSON: meaning that anything Jackson can regularly
         * serialize must convert to Strings anyway.
         * If we do find problem cases, we can start verifying them here,
         * but for now assume it all "just works".
         */
    }

    @Override
    public void valueFormat(JsonFormatVisitable handler, JavaType valueType)
        throws JsonMappingException
    {
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        handler.acceptJsonFormatVisitor(wrapper, valueType);
        _valueSchema = wrapper.getAvroSchema();
    }
}
