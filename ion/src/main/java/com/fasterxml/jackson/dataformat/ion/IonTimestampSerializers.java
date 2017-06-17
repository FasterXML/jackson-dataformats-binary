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
import java.util.Calendar;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

/**
 * A date serializer that uses native Ion timestamps instead of JSON strings.
 */
public class IonTimestampSerializers {

    public static class IonTimestampJavaDateSerializer extends StdScalarSerializer<java.util.Date>
    {
        private static final long serialVersionUID = 1L;

        public IonTimestampJavaDateSerializer() {
            super(java.util.Date.class);
        }

        @Override
        public void serialize(java.util.Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException
        {
            // Still respect writing dates as millis if desired
            if (serializerProvider.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
                jsonGenerator.writeNumber(date.getTime());
            } else {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTime(date);
                ((IonGenerator) jsonGenerator).writeDate(cal);
            }
        }
    }
    
    public static class IonTimestampSQLDateSerializer extends StdScalarSerializer<java.sql.Date>
    {
        private static final long serialVersionUID = 1L;

        public IonTimestampSQLDateSerializer() {
            super(java.sql.Date.class);
        }

        @Override
        public void serialize(java.sql.Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException
        {
            // Still respect writing dates as millis if desired
            if (serializerProvider.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
                jsonGenerator.writeNumber(date.getTime());
            } else {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTime(date);
                ((IonGenerator) jsonGenerator).writeDate(cal);
            }
        }
    }
}
