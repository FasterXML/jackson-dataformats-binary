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

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.TypeDeserializerBase;
import com.fasterxml.jackson.dataformat.ion.IonParser;

/**
 * This is a {@link com.fasterxml.jackson.databind.jsontype.TypeDeserializer} that reads typing metadata from Ion type
 * annotations. Annotations found are processed by the configured {@link TypeIdResolver} to provide a concrete Java
 * class, which is in turn used to find the appropriate {@link JsonDeserializer}, and execute it.
 * <p/>
 * If multiple annotations are found, the first annotation to resolve to a non-null {@link JavaType} using the
 * configured {@link TypeIdResolver} is used. Ion provides type annotations in alphabetic order.
 *
 * @see MultipleTypeIdResolver
 */
public class IonAnnotationTypeDeserializer extends TypeDeserializerBase
{
    private static final long serialVersionUID = 1L;

    public IonAnnotationTypeDeserializer(JavaType baseType, TypeIdResolver idRes,
            String typePropertyName, boolean typeIdVisible, JavaType defaultImpl) {
        super(baseType, idRes, typePropertyName, typeIdVisible, defaultImpl);
    }

    /**
     * Used for informational purposes and some serialization-implementation-specific logics (which do not concern us).
     * We have to return an enum which we cannot extend, and none of them accurately reflect what we do.
     *
     * @return null
     */
    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return null;
    }

    private IonParser ionParser(JsonParser p) throws JsonParseException {
        if (p instanceof IonParser) {
            return (IonParser) p;
        }
        throw new JsonParseException(p,
                "Can only use IonAnnotationTypeDeserializer with IonGenerator");
    }

    private Object _deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String[] typeIds = ionParser(p).getTypeAnnotations(); //cannot return null
        String typeIdToUse = null;
        TypeIdResolver typeIdResolver = super.getTypeIdResolver();
        if (typeIdResolver instanceof MultipleTypeIdResolver) {
            typeIdToUse = ((MultipleTypeIdResolver) typeIdResolver).selectId(typeIds);
        } else if (null != typeIdResolver) {
            // Possibly multiple ids, but we don't have a polymorphic resolver; pick the first one which resolves
            for (String typeId : typeIds) {
                JavaType type = typeIdResolver.typeFromId(ctxt, typeId);
                if (null != type) {
                    typeIdToUse = typeId;
                }
            }
        }
        JsonDeserializer<?> deserializer;
        if (null == typeIdToUse) {
            deserializer = _findDefaultImplDeserializer(ctxt);
        } else {
            deserializer = super._findDeserializer(ctxt, typeIdToUse);
        }
        // 22-Mar-2017, tatu: Getting `null` presumably means that no type id nor
        //   default impl found, but that this is ok (otherwise exception thrown)
        if (deserializer == null) {
            return null;
        }
        return deserializer.deserialize(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromObject(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return _deserialize(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromArray(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return _deserialize(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromScalar(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return _deserialize(p, ctxt);
    }

    @Override
    public Object deserializeTypedFromAny(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return _deserialize(p, ctxt);
    }

    @Override
    public TypeDeserializer forProperty(BeanProperty prop) {
        // We ignore the context information from BeanProperty.
        return this;
    }

}
