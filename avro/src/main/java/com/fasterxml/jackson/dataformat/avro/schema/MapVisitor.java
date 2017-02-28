package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;

public class MapVisitor extends JsonMapFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final DefinedSchemas _schemas;
    
    protected Schema _valueSchema;

    protected JavaType _keyType;

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
        AnnotatedClass ac = _provider.getConfig().introspectClassAnnotations(_keyType).getClassInfo();
        if (AvroSchemaHelper.isStringable(ac)) {
            return AvroSchemaHelper.stringableKeyMapSchema(_type, _keyType, _valueSchema);
        } else {
            throw new UnsupportedOperationException("Maps with non-stringable keys are not supported yet");
        }
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
        _keyType = keyType;
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
