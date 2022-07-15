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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ser.std.StdScalarSerializer;
import tools.jackson.dataformat.ion.IonGenerator;

import com.amazon.ion.IonValue;

/**
 * Serializer that knows how to serialize an IonValue.
 */
class IonValueSerializer extends StdScalarSerializer<IonValue>
{
    public IonValueSerializer() {
        super(IonValue.class);        
    }

    @Override
    public void serialize(IonValue value, JsonGenerator g, SerializerProvider provider)
    {
        IonGenerator joiGenerator = (IonGenerator) g;
        joiGenerator.writeValue(value);
    }
}
