package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Deserializer for {@link LocalDate} from and integer value.
 *
 * Deserialized value represents number of days from the unix epoch, 1 January 1970.
 *
 * Deserialization from string is not supported.
 */
public class AvroLocalDateDeserializer extends StdScalarDeserializer<LocalDate> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalDateDeserializer INSTANCE = new AvroLocalDateDeserializer();

    protected AvroLocalDateDeserializer() {
        super(LocalDate.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {
        switch (p.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                return fromLong(p.getLongValue());
            default:
                try {
                    return (LocalDate) context.handleUnexpectedToken(_valueClass, p);
                } catch (JsonMappingException e) {
                    throw e;
                } catch (IOException e) {
                    throw JsonMappingException.fromUnexpectedIOE(e);
                }
        }
    }

    private LocalDate fromLong(long longValue) {
        /**
         * Number of days from the unix epoch, 1 January 1970..
         */
        return LocalDate.ofEpochDay(longValue);
    }

}
