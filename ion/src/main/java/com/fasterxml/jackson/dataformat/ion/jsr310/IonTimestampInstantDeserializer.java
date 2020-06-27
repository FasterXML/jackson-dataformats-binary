package com.fasterxml.jackson.dataformat.ion.jsr310;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.function.BiFunction;

import com.amazon.ion.Timestamp;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Feature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * A deserializer for variants of java.time classes that represent a specific instant on the timeline
 * (Instant, OffsetDateTime, ZonedDateTime) which supports deserializing from an Ion timestamp value.
 *
 * @param <T>  The type of a instant class that can be deserialized.
 */
public class IonTimestampInstantDeserializer<T extends Temporal> extends StdScalarDeserializer<T> 
        implements ContextualDeserializer {

    private static final long serialVersionUID = 1L;

    public static final IonTimestampInstantDeserializer<Instant> INSTANT = 
            new IonTimestampInstantDeserializer<>(Instant.class, (instant, zoneID) -> instant);

    public static final IonTimestampInstantDeserializer<OffsetDateTime> OFFSET_DATE_TIME =
            new IonTimestampInstantDeserializer<>(OffsetDateTime.class, OffsetDateTime::ofInstant);

    public static final IonTimestampInstantDeserializer<ZonedDateTime> ZONED_DATE_TIME =
            new IonTimestampInstantDeserializer<>(ZonedDateTime.class, ZonedDateTime::ofInstant);

    protected final BiFunction<Instant, ZoneId, T> fromInstant;

    /**
     * Flag for <code>JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE</code>
     */
    protected final Boolean adjustToContextTZOverride;

    protected IonTimestampInstantDeserializer(Class<T> vc, BiFunction<Instant, ZoneId, T> fromInstant) {
        super(vc);
        this.fromInstant = fromInstant;
        this.adjustToContextTZOverride = null;
    }

    protected IonTimestampInstantDeserializer(IonTimestampInstantDeserializer<T> base, 
            Boolean adjustToContextTZOverride) {

        super(base.handledType());
        this.fromInstant = base.fromInstant;
        this.adjustToContextTZOverride = adjustToContextTZOverride;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
        final ZoneId defaultZoneId = context.getTimeZone().toZoneId().normalized();
        switch (p.getCurrentToken()) {
        case VALUE_NUMBER_FLOAT:
            return fromDecimal(p.getDecimalValue(), defaultZoneId);
        case VALUE_NUMBER_INT:
            return fromLong(p.getLongValue(), defaultZoneId, context);
        case VALUE_EMBEDDED_OBJECT:
            final Object embeddedObject = p.getEmbeddedObject();
            if (Timestamp.class.isAssignableFrom(embeddedObject.getClass())) {
                return fromTimestamp((Timestamp)embeddedObject, defaultZoneId);
            }
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

    @Override
    public JsonDeserializer<T> createContextual(DeserializationContext ctxt, BeanProperty property)
            throws JsonMappingException {

        final JsonFormat.Value format = findFormatOverrides(ctxt, property, handledType());
        if (format != null) {
            return new IonTimestampInstantDeserializer<T>(this, 
                    format.getFeature(Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE));
        }
        return this;
    }

    private T fromDecimal(BigDecimal decimalValue, ZoneId defaultZoneId) {
        final Instant instant = TimestampUtils.fromFractionalSeconds(decimalValue);
        return fromInstant.apply(instant, defaultZoneId);
    }

    private T fromLong(long longValue, ZoneId defaultZoneId, DeserializationContext context) {
        if(context.isEnabled(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)){
            return fromInstant.apply(Instant.ofEpochSecond(longValue, 0), defaultZoneId);
        }
        return fromInstant.apply(Instant.ofEpochMilli(longValue), defaultZoneId);
    }

    private T fromTimestamp(Timestamp timestamp, ZoneId defaultZoneId) {
        final Instant instant = TimestampUtils.toInstant(timestamp);
        final ZoneId zoneId = getZoneId(timestamp, defaultZoneId);
        return fromInstant.apply(instant, zoneId);
    }

    private ZoneId getZoneId(Timestamp timestamp, ZoneId defaultZoneId) {
        if (Boolean.TRUE.equals(adjustToContextTZOverride) 
                || null == timestamp.getLocalOffset()
                || Instant.class.equals(_valueClass)) {

            return defaultZoneId;
        }
        final int localOffsetMinutes = timestamp.getLocalOffset();
        return ZoneOffset.ofTotalSeconds(localOffsetMinutes * 60);
    }
}
