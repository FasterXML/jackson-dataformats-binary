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

package com.fasterxml.jackson.dataformat.ion;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers.DateDeserializer;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers.SqlDateDeserializer;
import software.amazon.ion.Timestamp;

/**
 * A date deserializer that uses native Ion timestamps instead of JSON strings.
 */
public class IonTimestampDeserializers {

    public static class IonTimestampJavaDateDeserializer extends DateDeserializer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.util.Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
        {
            if (p.hasToken(JsonToken.VALUE_EMBEDDED_OBJECT)) {
                Object embedded = p.getEmbeddedObject();
                if (embedded instanceof Timestamp) {
                    return ((Timestamp) embedded).dateValue();
                }
            }
            return super.deserialize(p, ctxt);
        }
    }
    
    public static class IonTimestampSQLDateDeserializer extends SqlDateDeserializer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.sql.Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
        {
            if (p.hasToken(JsonToken.VALUE_EMBEDDED_OBJECT)) {
                Object embedded = p.getEmbeddedObject();
                if (embedded instanceof Timestamp) {
                    return new java.sql.Date(((Timestamp) embedded).dateValue().getTime());
                }
            }
            return super.deserialize(p, ctxt);
        }
    }
}
