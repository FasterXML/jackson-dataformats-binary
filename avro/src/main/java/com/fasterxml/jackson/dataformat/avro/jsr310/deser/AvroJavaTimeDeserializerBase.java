package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import java.time.ZoneId;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;

import static com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT;

public abstract class AvroJavaTimeDeserializerBase<T> extends StdScalarDeserializer<T> {

    protected AvroJavaTimeDeserializerBase(Class<T> supportedType) {
        super(supportedType);
    }

    @Override
    public LogicalType logicalType() {
        return LogicalType.DateTime;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws JacksonException
    {
        if (p.currentToken() == VALUE_NUMBER_INT) {
            final ZoneId defaultZoneId = context.getTimeZone().toZoneId().normalized();
            return fromLong(p.getLongValue(), defaultZoneId);
        } else {
            return (T) context.handleUnexpectedToken(_valueClass, p);
        }
    }

    protected abstract T fromLong(long longValue, ZoneId defaultZoneId);
}
