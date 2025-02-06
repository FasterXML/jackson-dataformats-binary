/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion.polymorphism;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.*;

/**
 * A {@link TypeResolverBuilder} which produces {@link TypeSerializer}s and {@link TypeDeserializer}s that use
 * Ion type annotations to communicate type metadata. Jackson polymorphism, the Ion way.
 *<p>
 * This TypeResolverBuilder expects to be initialized with a functional {@link TypeIdResolver}, and will serialize
 * type information (and deserialize to something other than the default type) when it resolves the provided
 * {@link JavaType} to a non-null type identifier, and vice versa.
 */
public class IonAnnotationTypeResolverBuilder
    implements TypeResolverBuilder<IonAnnotationTypeResolverBuilder>
{
    private Class<?> defaultImpl;
    private TypeIdResolver typeIdResolver;

    /**
     * Whether type id should be exposed to deserializers or not
     */
    private boolean typeIdVisible;

    public IonAnnotationTypeResolverBuilder() {
    }

    // @since 2.13.1
    protected IonAnnotationTypeResolverBuilder(IonAnnotationTypeResolverBuilder base,
            Class<?> defaultImpl) {
        typeIdResolver = base.typeIdResolver;
        typeIdVisible = base.typeIdVisible;
        this.defaultImpl = defaultImpl;
    }

    @Override
    public Class<?> getDefaultImpl() {
        return defaultImpl;
    }

    /**
     * Creates a Jackson {@link TypeSerializer}. Note that while Jackson type serializers are responsible for writing
     * opening and closing metadata for types *in addition* to any type information, they are not involved with writing
     * actual object data.
     *
     * @param config
     * @param baseType
     * @param subtypes
     */
    @Override
    public TypeSerializer buildTypeSerializer(SerializationConfig config, JavaType baseType,
            Collection<NamedType> subtypes) {
        return new IonAnnotationTypeSerializer(typeIdResolver);
    }

    /**
     * Creates a Jackson {@link TypeDeserializer}. Unlike type serializers, deserializers are responsible for
     * *all* steps of value deserialization: read type information, find the actual object deserializer, and run it.
     *
     * @param config
     * @param baseType
     * @param subtypes
     */
    @Override
    public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
        JavaType defImplType = (defaultImpl == null) ? null
                : config.constructSpecializedType(baseType, defaultImpl);
        return new IonAnnotationTypeDeserializer(baseType,
                typeIdResolver, null, typeIdVisible, defImplType);
    }

    @Override
    public IonAnnotationTypeResolverBuilder init(JsonTypeInfo.Id idType, TypeIdResolver res) {
        typeIdResolver = res;
        return this;
    }

    @Override
    public IonAnnotationTypeResolverBuilder inclusion(JsonTypeInfo.As includeAs) {
        return this;
    }

    @Override
    public IonAnnotationTypeResolverBuilder typeProperty(String propName) {
        return this;
    }

    @Override
    public IonAnnotationTypeResolverBuilder defaultImpl(Class<?> di) {
        this.defaultImpl = di;
        return this;
    }

    @Override
    public IonAnnotationTypeResolverBuilder typeIdVisibility(boolean isVisible) {
        typeIdVisible = isVisible;
        return this;
    }

    @Override // @since 2.13.1
    public IonAnnotationTypeResolverBuilder withDefaultImpl(Class<?> newDefaultImpl) {
        if (newDefaultImpl == defaultImpl) {
            return this;
        }
        return new IonAnnotationTypeResolverBuilder(this, defaultImpl);
    }

    @Override // since 2.16, modified from jackson-databind/StdTypeResolverBuilder
    public IonAnnotationTypeResolverBuilder withSettings(JsonTypeInfo.Value settings)
    {
        // We don't have most of relevant things, just default implementation so:
        defaultImpl = settings.getDefaultImpl();
        typeIdVisible = settings.getIdVisible();
        return this;
    }
}
