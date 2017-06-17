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
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.dataformat.ion.IonGenerator;

/**
 * This is a {@link TypeSerializer} that places typing metadata in Ion type annotations. It requires that the underlying
 * {@link JsonGenerator} is actually a {@link com.fasterxml.jackson.dataformat.ion.IonGenerator}.
 *
 * Type serializers are responsible for handling the preamble and postamble of values, in addition to any possible
 * typing metadata (probably because type metadata can affect the pre/postamble content) -- in other words, once a
 * {@link TypeSerializer} gets involved, serializers skip normal pre/postambles and assume the TypeSerializer will do it
 * instead. This is why we have to do more than write type metadata in our writeTypePrefix/Suffix* implementations.
 *
 * @see MultipleTypeIdResolver
 */
public class IonAnnotationTypeSerializer extends TypeSerializer {

    private final TypeIdResolver typeIdResolver;

    IonAnnotationTypeSerializer(TypeIdResolver typeIdResolver) {
        this.typeIdResolver = typeIdResolver;
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return null;
    }

    @Override
    public String getPropertyName() {
        return null;
    }

    @Override
    public TypeIdResolver getTypeIdResolver() {
        return typeIdResolver;
    }

    private IonGenerator ionGenerator(JsonGenerator g) throws JsonGenerationException {
        if (g instanceof IonGenerator) {
            return (IonGenerator) g;
        }
        throw new JsonGenerationException("Can only use IonTypeSerializer with IonGenerator", g);
    }

    @Override
    public void writeTypePrefixForScalar(Object value, JsonGenerator g) throws IOException {
    }

    @Override
    public void writeTypePrefixForObject(Object value, JsonGenerator g) throws IOException {
        TypeIdResolver resolver = getTypeIdResolver();
        if (resolver instanceof MultipleTypeIdResolver) {
            String[] ids = ((MultipleTypeIdResolver)resolver).idsFromValue(value);
            for (String id : ids) {
                ionGenerator(g).annotateNextValue(id);
            }
        } else {
            String id = getTypeIdResolver().idFromValue(value);
            if (null != id && !id.isEmpty()) {
                ionGenerator(g).annotateNextValue(id);
            }
        }
        g.writeStartObject(); // standard
    }

    @Override
    public void writeTypePrefixForArray(Object value, JsonGenerator g) throws IOException {
        g.writeStartArray(); // standard
    }

    @Override
    public void writeTypeSuffixForScalar(Object value, JsonGenerator g) throws IOException {
    }

    @Override
    public void writeTypeSuffixForObject(Object value, JsonGenerator g) throws IOException {
        g.writeEndObject(); // standard
    }

    @Override
    public void writeTypeSuffixForArray(Object value, JsonGenerator g) throws IOException {
        g.writeEndArray(); // standard
    }

    
    @Override
    public TypeSerializer forProperty(BeanProperty prop) {
        // We ignore the context information from BeanProperty.
        return this;
    }

    
    @Override
    public void writeCustomTypePrefixForScalar(Object value,
            JsonGenerator g, String typeId) throws IOException {
        // There is no special prefix or suffix for Ion Annotations. 
    }

    @Override
    public void writeCustomTypePrefixForObject(Object value,
            JsonGenerator g, String typeId) throws IOException {
        // There is no special prefix or suffix for Ion Annotations. 
    }

    @Override
    public void writeCustomTypePrefixForArray(Object value, JsonGenerator g,
            String typeId) throws IOException {
        // There is no special prefix or suffix for Ion Annotations. 
    }

    @Override
    public void writeCustomTypeSuffixForScalar(Object value,
            JsonGenerator g, String typeId) throws IOException {
        // There is no special prefix or suffix for Ion Annotations. 
    }

    @Override
    public void writeCustomTypeSuffixForObject(Object value,
            JsonGenerator g, String typeId) throws IOException {
        // There is no special prefix or suffix for Ion Annotations. 
    }

    @Override
    public void writeCustomTypeSuffixForArray(Object value, JsonGenerator g,
            String typeId) throws IOException {
        // There is no special prefix or suffix for Ion Annotations. 
    }
}
