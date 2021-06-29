package com.fasterxml.jackson.dataformat.avro.jsr310.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.time.LocalTime;

/**
 * Serializer for {@link LocalTime} into int value.
 *
 * Serialized value represents time of day, with no reference to a particular calendar,
 * time zone or date, where the int stores the number of milliseconds after midnight, 00:00:00.000.
 *
 * Note: In combination with {@link com.fasterxml.jackson.dataformat.avro.schema.DateTimeVisitor} it aims to produce
 * Avro schema with type int and logicalType time-millis:
 *  {
 *   "type" : "int",
 *   "logicalType" : "time-millis"
 * }
 *
 * Serialization to string is not supported.
 */
public class AvroLocalTimeSerializer extends StdScalarSerializer<LocalTime> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalTimeSerializer INSTANCE = new AvroLocalTimeSerializer();

    protected AvroLocalTimeSerializer() {
        super(LocalTime.class);
    }

    @Override
    public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        /**
         * Number of milliseconds, with no reference to a particular calendar, time zone or date, after
         * midnight, 00:00:00.000.
         */
        long milliOfDay = value.toNanoOfDay() / 1000_000L;
        gen.writeNumber(milliOfDay);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.INT);
        }
    }

}
