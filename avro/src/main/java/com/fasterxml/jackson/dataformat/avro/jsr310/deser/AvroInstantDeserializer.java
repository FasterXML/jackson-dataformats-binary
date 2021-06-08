package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.function.BiFunction;

/**
 * Deserializer for variants of java.time classes (Instant, OffsetDateTime, ZonedDateTime) from an integer value.
 *
 * Deserialized value represents an instant on the global timeline, independent of a particular time zone or
 * calendar, with a precision of one millisecond from the unix epoch, 1 January 1970 00:00:00.000 UTC.
 * Time zone information is lost at serialization. Time zone data types receives time zone from deserialization context.
 *
 * Deserialization from string is not supported.
 *
 * @param <T> The type of a instant class that can be deserialized.
 */
public class AvroInstantDeserializer<T extends Temporal> extends StdScalarDeserializer<T> {

    private static final long serialVersionUID = 1L;

    public static final AvroInstantDeserializer<Instant> INSTANT =
            new AvroInstantDeserializer<>(Instant.class, (instant, zoneID) -> instant);

    public static final AvroInstantDeserializer<OffsetDateTime> OFFSET_DATE_TIME =
            new AvroInstantDeserializer<>(OffsetDateTime.class, OffsetDateTime::ofInstant);

    public static final AvroInstantDeserializer<ZonedDateTime> ZONED_DATE_TIME =
            new AvroInstantDeserializer<>(ZonedDateTime.class, ZonedDateTime::ofInstant);

    protected final BiFunction<Instant, ZoneId, T> fromInstant;

    protected AvroInstantDeserializer(Class<T> t, BiFunction<Instant, ZoneId, T> fromInstant) {
        super(t);
        this.fromInstant = fromInstant;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
        final ZoneId defaultZoneId = context.getTimeZone().toZoneId().normalized();
        switch (p.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                return fromLong(p.getLongValue(), defaultZoneId);
            default:
                try {
                    return (T) context.handleUnexpectedToken(_valueClass, p);
                } catch (JsonMappingException e) {
                    throw e;
                } catch (IOException e) {
                    throw JsonMappingException.fromUnexpectedIOE(e);
                }
        }
    }

    private T fromLong(long longValue, ZoneId defaultZoneId) {
        /**
         * Number of milliseconds, independent of a particular time zone or calendar,
         * from 1 January 1970 00:00:00.000 UTC.
         */
        return fromInstant.apply(Instant.ofEpochMilli(longValue), defaultZoneId);
    }

}
