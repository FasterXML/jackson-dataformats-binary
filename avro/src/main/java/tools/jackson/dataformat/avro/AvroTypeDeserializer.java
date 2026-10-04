package tools.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.*;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.impl.TypeDeserializerBase;
import tools.jackson.dataformat.avro.schema.AvroSchemaHelper;

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
            ValueDeserializer<Object> deser = _findDeserializer(ctxt, AvroSchemaHelper.getTypeId(_baseType));
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
        // [dataformats-binary#812]: for "untyped" (`java.lang.Object`) values, read
        //   record with unresolvable type id as "natural" type (`Map`), as 2.x does.
        //   Not for other (`@Union`) base types: likely abstract, so it would just fail later
        if (_baseType.isJavaLangObject()) {
            return _baseType;
        }
        // `AvroTypeIdResolver` only returns `null` for type ids with no matching class:
        // say so, instead of generic "known type ids" description `super` would use
        String extraDesc = "no such class found";
        if (_property != null) {
            extraDesc = "%s (for POJO property '%s')".formatted(extraDesc,
                    _property.getName());
        }
        return ctxt.handleUnknownTypeId(_baseType, typeId, _idResolver, extraDesc);
    }
}
