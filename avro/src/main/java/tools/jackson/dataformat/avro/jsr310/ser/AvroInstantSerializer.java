package tools.jackson.dataformat.avro.jsr310.ser;

import java.time.*;
import java.time.temporal.Temporal;
import java.util.function.Function;

import tools.jackson.core.*;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializer for variants of java.time classes (Instant, OffsetDateTime, ZonedDateTime) into long value.
 *
 * Serialized value represents an instant on the global timeline, independent of a particular time zone or
 * calendar, with a precision of one millisecond from the unix epoch, 1 January 1970 00:00:00.000 UTC.
 * Please note that time zone information gets lost in this process. Upon reading a value back, we can only
 * reconstruct the instant, but not the original representation.
 *
 * Note: In combination with {@link tools.jackson.dataformat.avro.schema.AvroSchemaGenerator#enableLogicalTypes()}
 * it aims to produce Avro schema with type long and logicalType timestamp-millis:
 * {
 *   "type" : "long",
 *   "logicalType" : "timestamp-millis"
 * }
 *
 * {@link AvroInstantSerializer} does not support serialization to string.
 *
 * @param <T> The type of a instant class that can be serialized.
 */
public class AvroInstantSerializer<T extends Temporal> extends StdScalarSerializer<T>
{
    public static final AvroInstantSerializer<Instant> INSTANT =
            new AvroInstantSerializer<>(Instant.class, Function.identity());

    public static final AvroInstantSerializer<OffsetDateTime> OFFSET_DATE_TIME =
            new AvroInstantSerializer<>(OffsetDateTime.class, OffsetDateTime::toInstant);

    public static final AvroInstantSerializer<ZonedDateTime> ZONED_DATE_TIME =
            new AvroInstantSerializer<>(ZonedDateTime.class, ZonedDateTime::toInstant);

    private final Function<T, Instant> getInstant;

    protected AvroInstantSerializer(Class<T> t, Function<T, Instant> getInstant) {
        super(t);
        this.getInstant = getInstant;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        /**
         * Number of milliseconds, independent of a particular time zone or calendar,
         * from 1 January 1970 00:00:00.000 UTC.
         */
        final Instant instant = getInstant.apply(value);
        gen.writeNumber(instant.toEpochMilli());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JacksonException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.LONG);
        }
    }

}
