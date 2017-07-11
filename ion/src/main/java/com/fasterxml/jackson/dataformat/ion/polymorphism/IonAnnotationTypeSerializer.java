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

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.TypeSerializerBase;
import com.fasterxml.jackson.dataformat.ion.IonGenerator;

/**
 * This is a {@link TypeSerializer} that places typing metadata in Ion type annotations. It requires that the underlying
 * {@link JsonGenerator} is actually a {@link com.fasterxml.jackson.dataformat.ion.IonGenerator}.
 *<p>
 * Type serializers are responsible for handling the preamble and postamble of values, in addition to any possible
 * typing metadata (probably because type metadata can affect the pre/postamble content) -- in other words, once a
 * {@link TypeSerializer} gets involved, serializers skip normal pre/postambles and assume the TypeSerializer will do it
 * instead. This is why we have to do more than write type metadata in our writeTypePrefix/Suffix* implementations.
 *
 * @see MultipleTypeIdResolver
 */
public class IonAnnotationTypeSerializer extends TypeSerializerBase
{
//    private final TypeIdResolver typeIdResolver;

    IonAnnotationTypeSerializer(TypeIdResolver typeIdResolver) {
        super(typeIdResolver, null);
    }

    @Override
    public TypeSerializer forProperty(BeanProperty prop) {
        // We ignore the context information from BeanProperty.
        return this;
    }
    
    @Override
    public As getTypeInclusion() {
        // !!! 10-Jul-2017, tatu: Should vary appropriately, but...
        return As.PROPERTY;
    }

    /*
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
    }*/

    @Override
    protected final void _generateTypeId(WritableTypeId idMetadata) {
        Object id = idMetadata.id;
        if (id == null) {
            final Object value = idMetadata.forValue;
            TypeIdResolver resolver = getTypeIdResolver();
            if (resolver instanceof MultipleTypeIdResolver) {
                id = ((MultipleTypeIdResolver)resolver).idsFromValue(value);
            } else {
                Class<?> typeForId = idMetadata.forValueType;
                if (typeForId == null) {
                    id = idFromValue(value);
                } else {
                    id = idFromValueAndType(value, typeForId);
                }
            }
            idMetadata.id = id;
        }
    }
}
