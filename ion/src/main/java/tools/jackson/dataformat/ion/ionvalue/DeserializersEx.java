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

package tools.jackson.dataformat.ion.ionvalue;

import com.amazon.ion.IonContainer;
import com.amazon.ion.IonValue;

import tools.jackson.databind.*;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.module.SimpleDeserializers;
import tools.jackson.databind.type.CollectionType;

class DeserializersEx extends SimpleDeserializers
{
    private static final long serialVersionUID = 1L;

    private static final IonValueDeserializer ION_VALUE_DESERIALIZER = new IonValueDeserializer();

    @Override
    public ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config,
            BeanDescription.Supplier beanDescRef)
    {
        if (IonValue.class.isAssignableFrom(type.getRawClass())) {
            return ION_VALUE_DESERIALIZER;
        }
        return super.findBeanDeserializer(type, config, beanDescRef);
    }

    @Override
    public ValueDeserializer<?>
            findCollectionDeserializer(CollectionType type, DeserializationConfig config,
                    BeanDescription.Supplier beanDescRef,
                    TypeDeserializer elementTypeDeserializer, ValueDeserializer<?> elementDeserializer)
    {
        if (IonContainer.class.isAssignableFrom(type.getRawClass())) {
            return ION_VALUE_DESERIALIZER;
        }
        return super.findCollectionDeserializer(type, config, beanDescRef,
                elementTypeDeserializer, elementDeserializer);
    }
}
