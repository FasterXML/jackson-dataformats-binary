package com.fasterxml.jackson.dataformat.avro.jsr310;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.function.Function;

/**
 * A serializer for variants of java.time classes that represent a specific instant on the timeline
 * (Instant, OffsetDateTime, ZonedDateTime) which supports serialization to Avro long type and logicalType.
 *
 * See: http://avro.apache.org/docs/current/spec.html#Logical+Types
 *
 * Note: {@link AvroInstantSerializer} does not support serialization to string.
 *
 * @param <T> The type of a instant class that can be serialized.
 */
public class AvroInstantSerializer<T extends Temporal> extends StdScalarSerializer<T>
        implements ContextualSerializer {

    private static final long serialVersionUID = 1L;

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
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        final Instant instant = getInstant.apply(value);
        gen.writeNumber(instant.toEpochMilli());
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        return this;
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.LONG);
        }
    }

}
