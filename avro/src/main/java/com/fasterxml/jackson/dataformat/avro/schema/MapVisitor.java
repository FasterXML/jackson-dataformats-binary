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

        Schema schema = Schema.createMap(_valueSchema);

        // add the key type if there is one
        if (_keyType != null && AvroSchemaHelper.isStringable(getProvider()
                                                                  .getConfig()
                                                                  .introspectClassAnnotations(_keyType)
                                                                  .getClassInfo())) {
            schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_KEY_CLASS, AvroSchemaHelper.getTypeId(_keyType));
        } else if (_keyType != null && !_keyType.isEnumType()) {
            // Avro handles non-stringable keys by converting the map to an array of key/value records
            // TODO add support for these in the schema, and custom serializers / deserializers to handle map restructuring
            throw new UnsupportedOperationException(
                "Key " + _keyType + " is not stringable and non-stringable map keys are not supported yet.");
        }

        return schema;
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
