package com.fasterxml.jackson.dataformat.ion.polymorphism;

import java.util.Collection;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * A replacement for {@link IonAnnotationTypeResolverBuilder} which uses Jackson {@link StdTypeResolverBuilder}. This
 * allows for configuration via standard Jackson type annotations and removes the need for using
 * {@link IonAnnotationIntrospector} and hence no additional Module. See {@link JsonTypeInfoAnnotationsTest} for example
 * usage.
 */
public class IonAnnotationStdTypeResolverBuilder extends StdTypeResolverBuilder {

    @Override
    public TypeSerializer buildTypeSerializer(
            SerializationConfig config,
            JavaType baseType,
            Collection<NamedType> subtypes) {
        return new IonAnnotationTypeSerializer(
                idResolver(
                        config,
                        baseType,
                        subtypes,
                        true,    // Indicates the id resolver is for serialization
                        false)); // Indicates the id resolver is not for deserialization
    }

    @Override
    public TypeDeserializer buildTypeDeserializer(
            DeserializationConfig config,
            JavaType baseType,
            Collection<NamedType> subtypes) {

        final JavaType defaultImpl;
        if (_defaultImpl == null) {
            defaultImpl = null;
        } else {
            defaultImpl = config.getTypeFactory() .constructSpecializedType(baseType, _defaultImpl);
        }

        return new IonAnnotationTypeDeserializer(
                baseType,
                idResolver(
                        config,
                        baseType,
                        subtypes,
                        false, // Indicates the id resolver is not for serialization
                        true), // Indicates the id resolver is for deserialization
                _typeProperty,
                _typeIdVisible,
                defaultImpl);
    }

}
