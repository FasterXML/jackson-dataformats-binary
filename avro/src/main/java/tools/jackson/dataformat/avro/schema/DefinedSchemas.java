package tools.jackson.dataformat.avro.schema;

import java.util.*;

import org.apache.avro.Schema;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;

/**
 * Simple container for Schemas that have already been generated during
 * generation process; used to share definitions.
 */
public class DefinedSchemas
{
    protected final Map<JavaType, Schema> _schemas = new LinkedHashMap<>();

    protected SerializationContext _serializationContext;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    public DefinedSchemas() { }

    public void setContext(SerializationContext ctxt) {
        _serializationContext = ctxt;
    }

    public SerializationContext getContext() {
        return _serializationContext;
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
