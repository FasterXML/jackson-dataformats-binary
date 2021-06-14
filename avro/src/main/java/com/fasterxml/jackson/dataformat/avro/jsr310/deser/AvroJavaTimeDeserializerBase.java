package com.fasterxml.jackson.dataformat.avro.jsr310.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.time.ZoneId;

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
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
        final ZoneId defaultZoneId = context.getTimeZone().toZoneId().normalized();
        switch (p.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                return fromLong(p.getLongValue(), defaultZoneId);
        }
        return (T) context.handleUnexpectedToken(_valueClass, p);
    }

    protected abstract T fromLong(long longValue, ZoneId defaultZoneId);
}
