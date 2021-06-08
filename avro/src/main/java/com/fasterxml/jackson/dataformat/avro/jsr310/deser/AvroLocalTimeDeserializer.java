package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Deserializer for {@link LocalTime} from an integer value.
 *
 * Deserialized value represents time of day, with no reference to a particular calendar,
 * time zone or date, where the int stores the number of milliseconds after midnight, 00:00:00.000.
 *
 * Deserialization from string is not supported.
 */
public class AvroLocalTimeDeserializer extends StdScalarDeserializer<LocalTime> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalTimeDeserializer INSTANCE = new AvroLocalTimeDeserializer();

    protected AvroLocalTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public LocalTime deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
        switch (p.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                return fromLong(p.getLongValue());
            default:
                try {
                    return (LocalTime) context.handleUnexpectedToken(_valueClass, p);
                } catch (JsonMappingException e) {
                    throw e;
                } catch (IOException e) {
                    throw JsonMappingException.fromUnexpectedIOE(e);
                }
        }
    }

    private LocalTime fromLong(long longValue) {
        /**
         * Number of milliseconds, with no reference to a particular calendar, time zone or date, after
         * midnight, 00:00:00.000.
         */
        return LocalTime.ofNanoOfDay(longValue * 1000_000L);
    }

}
