package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.*;

import org.apache.avro.Schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Simple container for Schemas that have already been generated during
 * generation process; used to share definitions.
 */
public class DefinedSchemas
{
    protected final Map<JavaType, Schema> _schemas = new LinkedHashMap<JavaType, Schema>();

    protected SerializerProvider _provider;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */
    
    public DefinedSchemas() { }

    public void setProvider(SerializerProvider provider) {
        _provider = provider;
    }
    
    public SerializerProvider getProvider() {
        return _provider;
    }

    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */
    
    public Schema findSchema(JavaType type) {
        return _schemas.get(type);
    }

    public void addSchema(JavaType type, Schema schema) {
        Schema old = _schemas.put(type, schema);
        if (old != null) {
            throw new IllegalStateException("Trying to re-define schema for type "+type);
        }
    }
}
