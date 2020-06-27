package com.fasterxml.jackson.dataformat.ion.jsr310;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Feature;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.fasterxml.jackson.dataformat.ion.IonGenerator;

/**
 * A serializer for variants of java.time classes that represent a specific instant on the timeline
 * (Instant, OffsetDateTime, ZonedDateTime) which supports serializing to an Ion timestamp value.
 *
 * @param <T>  The type of a instant class that can be serialized.
 */
public class IonTimestampInstantSerializer<T extends Temporal> extends StdScalarSerializer<T>
        implements ContextualSerializer {

    private static final long serialVersionUID = 1L;

    public static final IonTimestampInstantSerializer<Instant> INSTANT = 
            new IonTimestampInstantSerializer<>(Instant.class, 
            		Function.identity(),
                    (instant) -> ZoneOffset.UTC,
                    (instant, zoneId) -> instant.atZone(zoneId).getOffset());
    
    public static final IonTimestampInstantSerializer<OffsetDateTime> OFFSET_DATE_TIME = 
            new IonTimestampInstantSerializer<>(OffsetDateTime.class,
            		OffsetDateTime::toInstant,
                    OffsetDateTime::getOffset,
                    (offsetDateTime, zoneId) -> offsetDateTime.atZoneSameInstant(zoneId).getOffset());
    
    /**
     * A serializer for ZoneDateTime's. NOTE: Ion timestamp values can only represent offset values
     * so specific time zone values will be converted to an equivalent offset value.
     */
    public static final IonTimestampInstantSerializer<ZonedDateTime> ZONED_DATE_TIME = 
            new IonTimestampInstantSerializer<>(ZonedDateTime.class,
            		ZonedDateTime::toInstant,
            		ZonedDateTime::getOffset,
            		(zonedDateTime, zoneId) -> zonedDateTime.withZoneSameInstant(zoneId).getOffset());
    
    private final Function<T, Instant> getInstant;
    private final Function<T, ZoneOffset> getOffset;
    private final BiFunction<T, ZoneId, ZoneOffset> getOffsetAtZoneId;
    
    /**
     * ZoneId equivalent of <code>JsonFormat.timezone</code>
     */
    private final ZoneId zoneIdOverride;
    
    /**
     * Flag for <code>JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS</code>
     */
    private final Boolean writeDateTimestampsAsNanosOverride;

    protected IonTimestampInstantSerializer(Class<T> t, 
            Function<T, Instant> getInstant,
            Function<T, ZoneOffset> getOffset,
            BiFunction<T, ZoneId, ZoneOffset> getOffsetAtZoneId) {

        super(t);
        this.getInstant = getInstant;
        this.getOffset = getOffset;
        this.getOffsetAtZoneId = getOffsetAtZoneId;
        this.zoneIdOverride = null;
        this.writeDateTimestampsAsNanosOverride = null;
    }
    
    protected IonTimestampInstantSerializer(IonTimestampInstantSerializer<T> base,
            ZoneId zoneIdOverride,
            Boolean writeDateTimestampsAsNanosOverride) {

        super(base.handledType());
        this.getInstant = base.getInstant;
        this.getOffset = base.getOffset;
        this.getOffsetAtZoneId = base.getOffsetAtZoneId;
        this.zoneIdOverride = zoneIdOverride;
        this.writeDateTimestampsAsNanosOverride = writeDateTimestampsAsNanosOverride;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        final Instant instant = getInstant.apply(value);
        if (provider.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
            if (shouldWriteTimestampsAsNanos(provider)) {
                gen.writeNumber(TimestampUtils.getFractionalSeconds(instant));
            } else {
                gen.writeNumber(instant.toEpochMilli());
            }
        } else {
            final ZoneOffset offset = getOffset(value);
            ((IonGenerator)gen).writeValue(TimestampUtils.toTimestamp(instant, offset));
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        
        final JsonFormat.Value format = findFormatOverrides(prov, property, handledType());
        if (format != null) {
            return new IonTimestampInstantSerializer<>(this,
                    format.getTimeZone() == null ? null : format.getTimeZone().toZoneId(),
                    format.getFeature(Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS));
        }
        return this;
    }

    private boolean shouldWriteTimestampsAsNanos(SerializerProvider provider) {
        if (Boolean.FALSE.equals(writeDateTimestampsAsNanosOverride)) {
            return false;
        }
        return provider.isEnabled(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                || Boolean.TRUE.equals(writeDateTimestampsAsNanosOverride);
    }

    private ZoneOffset getOffset(T value) {
        if (null != zoneIdOverride) {
             return getOffsetAtZoneId.apply(value, zoneIdOverride);
        }
        return getOffset.apply(value);
    }
}
