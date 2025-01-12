package tools.jackson.dataformat.avro.jsr310.deser;

import java.time.*;

/**
 * Deserializer for {@link LocalDateTime} from an integer value.
 *
 * Deserialized value represents timestamp in a local timezone, regardless of what specific time zone
 * is considered local, with a precision of one millisecond from 1 January 1970 00:00:00.000.
 *
 * Deserialization from string is not supported.
 */
public class AvroLocalDateTimeDeserializer extends AvroJavaTimeDeserializerBase<LocalDateTime>
{
    public static final AvroLocalDateTimeDeserializer INSTANCE = new AvroLocalDateTimeDeserializer();

    protected AvroLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    protected LocalDateTime fromLong(long longValue, ZoneId defaultZoneId) {
        /**
         * Number of milliseconds in a local timezone, regardless of what specific time zone is considered local,
         * from 1 January 1970 00:00:00.000.
         */
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneOffset.ofTotalSeconds(0));
    }

}
