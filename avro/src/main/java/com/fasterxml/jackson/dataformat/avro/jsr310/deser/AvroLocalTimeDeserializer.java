package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Deserializer for {@link LocalTime} from an integer value.
 *
 * Deserialized value represents time of day, with no reference to a particular calendar,
 * time zone or date, where the int stores the number of milliseconds after midnight, 00:00:00.000.
 *
 * Deserialization from string is not supported.
 */
public class AvroLocalTimeDeserializer extends AvroJavaTimeDeserializerBase<LocalTime> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalTimeDeserializer INSTANCE = new AvroLocalTimeDeserializer();

    protected AvroLocalTimeDeserializer() {
        super(LocalTime.class);
    }

    @Override
    protected LocalTime fromLong(long longValue, ZoneId defaultZoneId) {
        /**
         * Number of milliseconds, with no reference to a particular calendar, time zone or date, after
         * midnight, 00:00:00.000.
         */
        return LocalTime.ofNanoOfDay(longValue * 1000_000L);
    }

}
