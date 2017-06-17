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

package com.fasterxml.jackson.dataformat.ion.ionvalue;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.type.CollectionType;
import software.amazon.ion.IonContainer;
import software.amazon.ion.IonValue;

class DeserializersEx extends SimpleDeserializers
{
    private static final long serialVersionUID = 1L;

    private static final IonValueDeserializer ION_VALUE_DESERIALIZER = new IonValueDeserializer();

    @Override
    public JsonDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config,
            BeanDescription beanDesc) throws JsonMappingException
    {
        if (IonValue.class.isAssignableFrom(type.getRawClass())) {
            return ION_VALUE_DESERIALIZER;
        }
        return super.findBeanDeserializer(type, config, beanDesc);
    }

    @Override
    public JsonDeserializer<?>
            findCollectionDeserializer(CollectionType type, DeserializationConfig config, BeanDescription beanDesc,
                    TypeDeserializer elementTypeDeserializer, JsonDeserializer<?> elementDeserializer)
        throws JsonMappingException
    {
        if (IonContainer.class.isAssignableFrom(type.getRawClass())) {
            return ION_VALUE_DESERIALIZER;
        }
        return super.findCollectionDeserializer(type, config, beanDesc,
                elementTypeDeserializer, elementDeserializer);
    }
}
