package tools.jackson.dataformat.avro.jsr310.deser;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Deserializer for {@link LocalDate} from and integer value.
 *
 * Deserialized value represents number of days from the unix epoch, 1 January 1970.
 *
 * Deserialization from string is not supported.
 */
public class AvroLocalDateDeserializer extends AvroJavaTimeDeserializerBase<LocalDate>
{
    public static final AvroLocalDateDeserializer INSTANCE = new AvroLocalDateDeserializer();

    protected AvroLocalDateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    protected LocalDate fromLong(long longValue, ZoneId defaultZoneId) {
        /**
         * Number of days from the unix epoch, 1 January 1970..
         */
        return LocalDate.ofEpochDay(longValue);
    }

}
