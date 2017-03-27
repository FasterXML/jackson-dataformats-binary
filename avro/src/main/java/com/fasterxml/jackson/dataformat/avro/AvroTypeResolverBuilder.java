package com.fasterxml.jackson.dataformat.avro;

import java.util.Collection;

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
        super();
        typeIdVisibility(false).typeProperty("@class");
    }

    @Override
    public TypeSerializer buildTypeSerializer(SerializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
        // All type information is encoded in the schema, never in the data.
        return null;
    }

    @Override
    public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
        JavaType defaultImpl = null;
        if (getDefaultImpl() != null) {
            defaultImpl = config.constructType(getDefaultImpl());
        }

        return new AvroTypeDeserializer(baseType,
                                        idResolver(config, baseType, subtypes, true, true),
                                        getTypeProperty(),
                                        isTypeIdVisible(),
                                        defaultImpl
        );

    }

    @Override
    protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType, Collection<NamedType> subtypes, boolean forSer,
                                        boolean forDeser) {
        return new AvroTypeIdResolver(baseType, config.getTypeFactory(), subtypes);
    }
}
