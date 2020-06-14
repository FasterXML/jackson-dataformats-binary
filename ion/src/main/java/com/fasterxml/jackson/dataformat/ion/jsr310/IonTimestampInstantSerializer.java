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

public class IonTimestampInstantSerializer<T extends Temporal> extends StdScalarSerializer<T>
        implements ContextualSerializer {

    private static final long serialVersionUID = 1L;
    
    public static final IonTimestampInstantSerializer<Instant> INSTANT = 
            new IonTimestampInstantSerializer<>(Instant.class, 
                    new Function<Instant, Instant>() {
                        public Instant apply(Instant t) {
                            return t;
                        }
                    },
                    new Function<Instant, ZoneOffset>() {
                        @Override
                        public ZoneOffset apply(Instant t) {
                            return ZoneOffset.UTC;
                        }
                    },
                    new BiFunction<Instant, ZoneId, ZoneOffset>() {
                        @Override
                        public ZoneOffset apply(Instant t, ZoneId zoneId) {
                            return t.atZone(zoneId).getOffset();
                        }
                    });
    
    public static final IonTimestampInstantSerializer<OffsetDateTime> OFFSET_DATE_TIME = 
            new IonTimestampInstantSerializer<>(OffsetDateTime.class,
                    new Function<OffsetDateTime, Instant>() {
                        @Override
                        public Instant apply(OffsetDateTime t) {
                            return t.toInstant();
                        }
                    },
                    new Function<OffsetDateTime, ZoneOffset>() {
                        @Override
                        public ZoneOffset apply(OffsetDateTime t) {
                            return t.getOffset();
                        }
                    },
                    new BiFunction<OffsetDateTime, ZoneId, ZoneOffset>() {
                        @Override
                        public ZoneOffset apply(OffsetDateTime t, ZoneId zoneId) {
                            return t.atZoneSameInstant(zoneId).getOffset();
                        }
                    });
    
    public static final IonTimestampInstantSerializer<ZonedDateTime> ZONED_DATE_TIME = 
            new IonTimestampInstantSerializer<>(ZonedDateTime.class, 
                    new Function<ZonedDateTime, Instant>() {
                        @Override
                        public Instant apply(ZonedDateTime t) {
                            return t.toInstant();
                        }
                    },
                    new Function<ZonedDateTime, ZoneOffset>() {
                        @Override
                        public ZoneOffset apply(ZonedDateTime t) {
                            return t.getOffset();
                        }      
                    },
                    new BiFunction<ZonedDateTime, ZoneId, ZoneOffset>() {
                        @Override
                        public ZoneOffset apply(ZonedDateTime t, ZoneId zoneId) {
                            return t.withZoneSameInstant(zoneId).getOffset();
                        }
                    });
    
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
