package com.fasterxml.jackson.dataformat.avro;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * @since 2.9
 */
public class AvroTypeResolverBuilder extends StdTypeResolverBuilder
{
    protected AvroTypeResolverBuilder(JsonTypeInfo.Value config) {
        super(config);
    }

    public static AvroTypeResolverBuilder construct(JsonTypeInfo.Value config) {
        if (config == null) {
            config = JsonTypeInfo.Value.construct(JsonTypeInfo.Id.CUSTOM, // could be NONE, but there is type discriminator in Avro...
                    JsonTypeInfo.As.PROPERTY, // N/A for custom
                    "@class", // similarly, N/A
                    null, // defaultImpl
                    false); // id visible
        }
        // no use for annotation info, at this point?
        return new AvroTypeResolverBuilder(config);
    }

    @Override
    public TypeSerializer buildTypeSerializer(SerializerProvider ctxt, JavaType baseType,
            Collection<NamedType> subtypes) {
        // All type information is encoded in the schema, never in the data.
        return null;
    }

    @Override
    public TypeDeserializer buildTypeDeserializer(DeserializationContext ctxt, JavaType baseType,
            Collection<NamedType> subtypes)
    {
        Class<?> rawDefault = getDefaultImpl();
        JavaType defaultImpl = (rawDefault == null) ? null :
            ctxt.constructType(rawDefault);
        TypeIdResolver idRes = idResolver(ctxt, baseType, subTypeValidator(ctxt),
                subtypes, true, false);
        return new AvroTypeDeserializer(baseType,
                idRes, getTypeProperty(), isTypeIdVisible(), defaultImpl);
    }

    @Override
    protected TypeIdResolver idResolver(DatabindContext ctxt,
            JavaType baseType, PolymorphicTypeValidator subtypeValidator,
            Collection<NamedType> subtypes, boolean forSer, boolean forDeser) {
        return new AvroTypeIdResolver(baseType, ctxt.getTypeFactory(), subtypes);
    }
}
