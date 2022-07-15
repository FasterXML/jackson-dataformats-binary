package tools.jackson.dataformat.avro.jsr310.ser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializer for {@link LocalDateTime} into long value
 *
 * Serialized value represents timestamp in a local timezone, regardless of what specific time zone
 * is considered local, with a precision of one millisecond from 1 January 1970 00:00:00.000.
 *
 * Note: In combination with {@link tools.jackson.dataformat.avro.schema.AvroSchemaGenerator#enableLogicalTypes()}
 * it aims to produce Avro schema with type long and logicalType local-timestamp-millis:
 * {
 *   "type" : "long",
 *   "logicalType" : "local-timestamp-millis"
 * }
 *
 * Serialization to string is not supported.
 */
public class AvroLocalDateTimeSerializer extends StdScalarSerializer<LocalDateTime>
{
    public static final AvroLocalDateTimeSerializer INSTANCE = new AvroLocalDateTimeSerializer();

    protected AvroLocalDateTimeSerializer() {
        super(LocalDateTime.class);
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
        throws JacksonException
    {
        /**
         * Number of milliseconds in a local timezone, regardless of what specific time zone is considered local,
         * from 1 January 1970 00:00:00.000.
         */
        final Instant instant = value.toInstant(ZoneOffset.ofTotalSeconds(0));
        gen.writeNumber(instant.toEpochMilli());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
            throws JacksonException
    {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.LONG);
        }
    }

}
