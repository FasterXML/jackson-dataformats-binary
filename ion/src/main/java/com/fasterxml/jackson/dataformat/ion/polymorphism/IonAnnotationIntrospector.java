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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

/**
 * A Jackson {@link com.fasterxml.jackson.databind.AnnotationIntrospector} (essentially an interceptor for
 * serializer/deserializer construction) that provides type serializer/deserializers that write/read Ion type
 * annotations.
 * <p/>
 * The logic in this class is very similar to {@link com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector}!
 * We both look at the @JsonTypeResolver, etc annotations and try to make type resolvers.
 * <p/>
 * This class adds a {@code resolveAllTypes} override, which allows for universal polymorphism without needing
 * any annotations or mixins, and also permits top-level polymorphism -- deserialize to any object without providing its
 * actual type, as long as type information was serialized. (i.e., ObjectMapper#readValue(serializedData, Object.class))
 * <p/>
 * Note: the provided {@link com.fasterxml.jackson.databind.jsontype.TypeSerializer} will only write type annotations if the configured
 * {@link TypeIdResolver} returns non-null.
 * <p/>
 * Note: {@link com.fasterxml.jackson.databind.jsontype.TypeDeserializer} are actually full-on value deserializers -- all
 * deserialization logic goes through them (unlike TypeSerializers, which just write the type metadata).
 * <p/>
 */
public class IonAnnotationIntrospector extends NopAnnotationIntrospector {
    private static final long serialVersionUID = 1L;
    
    private final boolean resolveAllTypes;

    public IonAnnotationIntrospector(boolean resolveAllTypes) {
        this.resolveAllTypes = resolveAllTypes;
    }

    protected TypeIdResolver defaultIdResolver(MapperConfig<?> config, JavaType baseType) {
        return null;
    }

    protected boolean shouldResolveType(AnnotatedClass ac) {
        JsonTypeResolver typeResolverAnn = ac.getAnnotation(JsonTypeResolver.class);
        return null != typeResolverAnn && IonAnnotationTypeResolverBuilder.class.equals(typeResolverAnn.value());
    }

    /**
     * Provides a {@link TypeResolverBuilder} if the {@link AnnotatedClass} is enabled for type resolving, and a
     * {@link TypeIdResolver} can be instantiated.
     * <p/>
     * The AnnotatedClass is enabled for type resolving if either {@code resolveAllTypes} is set, or shouldResolveType()
     * returns true.
     * <p/>
     * We look for a TypeIdResolver by checking for a {@link JsonTypeIdResolver} annotation, and fallback to
     * {@code defaultIdResolver()}.
     *
     * @param config   a serialization or deserialization config object
     * @param ac       an AnnotatedClass representing a (statically configured) base type for type resolution
     * @param baseType a JavaType representing the same class
     * @return a type resolver builder that reads and writes Ion type annotations, or null
     */
    @Override
    public TypeResolverBuilder<?> findTypeResolver(MapperConfig<?> config, AnnotatedClass ac, JavaType baseType) {
        if (baseType.isArrayType() || baseType.isContainerType() || baseType.isPrimitive()) {
            // Ion type annotations are not useful for these data types
            return null;
        }
        if (resolveAllTypes || shouldResolveType(ac)) {
            // First, find a TypeIdResolver. Check for annotation, then defaultIdResolver().
            JsonTypeIdResolver typeIdResolverAnn = ac.getAnnotation(JsonTypeIdResolver.class);
            TypeIdResolver typeIdResolver = null;
            if (null != typeIdResolverAnn) {
                typeIdResolver = config.typeIdResolverInstance(ac, typeIdResolverAnn.value());
            }
            if (null == typeIdResolver) {
                typeIdResolver = defaultIdResolver(config, baseType);
            }
            // If we still haven't found one, we can't move forward.
            if (null != typeIdResolver) {
                typeIdResolver.init(baseType);

                TypeResolverBuilder<?> typeResolverBuilder = new IonAnnotationTypeResolverBuilder();
                typeResolverBuilder.init(/* ignored */ null, typeIdResolver);
                typeResolverBuilder.defaultImpl(baseType.getRawClass());
                return typeResolverBuilder;
            }
        }
        return super.findTypeResolver(config, ac, baseType); // Nop probably returns null ;D
    }
}
