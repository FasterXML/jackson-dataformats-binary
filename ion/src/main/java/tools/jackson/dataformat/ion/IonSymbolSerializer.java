/*
 * Copyright 2011-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package tools.jackson.dataformat.ion;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.exc.StreamWriteException;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializes members as IonSymbols.
 *
 * Use annotation
 *
 * <pre>
 * &#64;JsonSerialize(using=IonSymbolSerializer.class)
 * </pre>
 *
 * on String members that should serialize at symbols (which amounts to
 * serializing without being surrounded by double-quotes)
 */
public class IonSymbolSerializer extends StdScalarSerializer<String>
{
    public IonSymbolSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator g, SerializerProvider provider) {
        if (IonGenerator.class.isAssignableFrom(g.getClass())) {
            ((IonGenerator) g).writeSymbol(value);
        } else {
            throw new StreamWriteException(g, "Can only use IonSymbolSerializer with IonGenerator");
        }
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) {
        visitStringFormat(visitor, typeHint);
    }
}
