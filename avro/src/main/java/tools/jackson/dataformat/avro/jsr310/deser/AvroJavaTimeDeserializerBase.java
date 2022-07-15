package tools.jackson.dataformat.avro.jsr310.deser;

import java.time.ZoneId;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.type.LogicalType;

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
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            final ZoneId defaultZoneId = context.getTimeZone().toZoneId().normalized();
            return fromLong(p.getLongValue(), defaultZoneId);
        } else {
            return (T) context.handleUnexpectedToken(_valueClass, p);
        }
    }

    protected abstract T fromLong(long longValue, ZoneId defaultZoneId);
}
