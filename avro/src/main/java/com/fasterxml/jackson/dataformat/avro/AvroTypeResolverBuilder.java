package com.fasterxml.jackson.dataformat.avro;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * @since 2.9
 */
public class AvroTypeResolverBuilder extends StdTypeResolverBuilder {

    public AvroTypeResolverBuilder() {
        super(JsonTypeInfo.Id.CUSTOM, // could be NONE, but there is type discriminator in Avro...
                JsonTypeInfo.As.PROPERTY, // N/A for custom
                "@class" // similarly, N/A
                );
    }

    @Override
    public TypeSerializer buildTypeSerializer(SerializationConfig config, JavaType baseType,
            Collection<NamedType> subtypes) {
        // All type information is encoded in the schema, never in the data.
        return null;
    }

    @Override
    public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType,
            Collection<NamedType> subtypes)
    {
        Class<?> rawDefault = getDefaultImpl();
        JavaType defaultImpl = (rawDefault == null) ? null : 
            config.constructType(rawDefault);
        return new AvroTypeDeserializer(baseType,
                idResolver(config, baseType, subtypes, true, true),
                getTypeProperty(),
                isTypeIdVisible(),
                defaultImpl
        );
    }

    @Override
    protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType,
                Collection<NamedType> subtypes, boolean forSer,
                boolean forDeser) {
        return new AvroTypeIdResolver(baseType, config.getTypeFactory(), subtypes);
    }
}
