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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Serializer for {@link LocalDateTime} into long value
 *
 * Serialized value represents timestamp in a local timezone, regardless of what specific time zone
 * is considered local, with a precision of one millisecond from 1 January 1970 00:00:00.000.
 *
 * Note: In combination with {@link com.fasterxml.jackson.dataformat.avro.schema.DateTimeVisitor} it aims to produce
 * Avro schema with type long and logicalType local-timestamp-millis:
 * {
 *   "type" : "long",
 *   "logicalType" : "local-timestamp-millis"
 * }
 *
 * Serialization to string is not supported.
 */
public class AvroLocalDateTimeSerializer extends StdScalarSerializer<LocalDateTime> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalDateTimeSerializer INSTANCE = new AvroLocalDateTimeSerializer();

    protected AvroLocalDateTimeSerializer() {
        super(LocalDateTime.class);
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        /**
         * Number of milliseconds in a local timezone, regardless of what specific time zone is considered local,
         * from 1 January 1970 00:00:00.000.
         */
        final Instant instant = value.toInstant(ZoneOffset.ofTotalSeconds(0));
        gen.writeNumber(instant.toEpochMilli());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.LONG);
        }
    }

}
