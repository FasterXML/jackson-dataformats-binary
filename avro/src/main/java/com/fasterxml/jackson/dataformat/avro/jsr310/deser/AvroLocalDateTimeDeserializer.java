package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Deserializer for {@link LocalDateTime} from an integer value.
 *
 * Deserialized value represents timestamp in a local timezone, regardless of what specific time zone
 * is considered local, with a precision of one millisecond from 1 January 1970 00:00:00.000.
 *
 * Deserialization from string is not supported.
 */
public class AvroLocalDateTimeDeserializer extends StdScalarDeserializer<LocalDateTime> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalDateTimeDeserializer INSTANCE = new AvroLocalDateTimeDeserializer();

    protected AvroLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
        switch (p.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                return fromLong(p.getLongValue());
            default:
                try {
                    return (LocalDateTime) context.handleUnexpectedToken(_valueClass, p);
                } catch (JsonMappingException e) {
                    throw e;
                } catch (IOException e) {
                    throw JsonMappingException.fromUnexpectedIOE(e);
                }
        }
    }

    private LocalDateTime fromLong(long longValue) {
        /**
         * Number of milliseconds in a local timezone, regardless of what specific time zone is considered local,
         * from 1 January 1970 00:00:00.000.
         */
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneOffset.ofTotalSeconds(0));
    }

}
