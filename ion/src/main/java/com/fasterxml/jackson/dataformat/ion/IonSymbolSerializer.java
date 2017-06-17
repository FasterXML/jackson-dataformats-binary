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

package com.fasterxml.jackson.dataformat.ion;

import java.io.IOException;
import java.lang.reflect.Type;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;


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
    private static final long serialVersionUID = 1L;

    public IonSymbolSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator g, SerializerProvider provider) throws IOException {
        if (IonGenerator.class.isAssignableFrom(g.getClass())) {
            ((IonGenerator) g).writeSymbol(value);
        } else {
            throw new JsonGenerationException("Can only use IonSymbolSerializer with IonGenerator", g);
        }
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint) throws JsonMappingException {
        return createSchemaNode("string", true);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        visitStringFormat(visitor, typeHint);
    }
}
