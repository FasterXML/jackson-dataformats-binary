package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.time.ZoneId;

import static com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT;

public abstract class AvroJavaTimeDeserializerBase<T> extends StdScalarDeserializer<T>
{
    private static final long serialVersionUID = 1L;

    protected AvroJavaTimeDeserializerBase(Class<T> supportedType) {
        super(supportedType);
    }

    @Override
    public LogicalType logicalType() {
        return LogicalType.DateTime;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
        if (p.getCurrentToken() == VALUE_NUMBER_INT) {
            final ZoneId defaultZoneId = context.getTimeZone().toZoneId().normalized();
            return fromLong(p.getLongValue(), defaultZoneId);
        } else {
            return (T) context.handleUnexpectedToken(_valueClass, p);
        }
    }

    protected abstract T fromLong(long longValue, ZoneId defaultZoneId);
}
