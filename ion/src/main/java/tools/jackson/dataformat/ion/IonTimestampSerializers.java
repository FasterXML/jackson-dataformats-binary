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

package tools.jackson.dataformat.ion;

import java.util.Calendar;
import java.util.TimeZone;

import tools.jackson.core.JsonGenerator;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * A date serializer that uses native Ion timestamps instead of JSON strings.
 */
public class IonTimestampSerializers {

    public static class IonTimestampJavaDateSerializer extends StdScalarSerializer<java.util.Date>
    {
        public IonTimestampJavaDateSerializer() {
            super(java.util.Date.class);
        }

        @Override
        public void serialize(java.util.Date date, JsonGenerator jsonGenerator, SerializationContext ctxt)
        {
            // Still respect writing dates as millis if desired
            if (ctxt.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)) {
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
        public IonTimestampSQLDateSerializer() {
            super(java.sql.Date.class);
        }

        @Override
        public void serialize(java.sql.Date date, JsonGenerator jsonGenerator, SerializationContext ctxt)
        {
            // Still respect writing dates as millis if desired
            if (ctxt.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)) {
                jsonGenerator.writeNumber(date.getTime());
            } else {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTime(date);
                ((IonGenerator) jsonGenerator).writeDate(cal);
            }
        }
    }
}
