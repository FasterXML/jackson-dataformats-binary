package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.TypeDeserializerBase;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper;

public class AvroTypeDeserializer extends TypeDeserializerBase
{
    protected AvroTypeDeserializer(JavaType baseType, TypeIdResolver idRes, String typePropertyName, boolean typeIdVisible,
                                   JavaType defaultImpl) {
        super(baseType, idRes, typePropertyName, typeIdVisible, defaultImpl);
    }

    protected AvroTypeDeserializer(TypeDeserializerBase src, BeanProperty property) {
        super(src, property);
    }

    @Override
    public TypeDeserializer forProperty(BeanProperty prop) {
        return new AvroTypeDeserializer(this, prop);
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        // Don't do any restructuring of the incoming JSON tokens
        return JsonTypeInfo.As.EXISTING_PROPERTY;
    }

    @Override
    public Object deserializeTypedFromObject(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return deserializeTypedFromAny(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromArray(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return deserializeTypedFromAny(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromScalar(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return deserializeTypedFromAny(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromAny(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        if (p.getTypeId() == null && getDefaultImpl() == null) {
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, AvroSchemaHelper.getTypeId(_baseType));
            if (deser == null) {
                ctxt.reportInputMismatch(_baseType, "No (native) type id found when one was expected for polymorphic type handling");
                return null;
            }
            return deser.deserialize(p, ctxt);
        }
        return _deserializeWithNativeTypeId(p, ctxt, p.getTypeId());
    }

    @Override
    protected JavaType _handleUnknownTypeId(DeserializationContext ctxt, String typeId)
        throws JacksonException
    {
        // 29-Jan-2018, tatu: No idea why this was added in 2.x.
        /*
        if (ctxt.hasValueDeserializerFor(_baseType, null)) {
            return _baseType;
        }
        */
        return super._handleUnknownTypeId(ctxt, typeId);
    }
}
