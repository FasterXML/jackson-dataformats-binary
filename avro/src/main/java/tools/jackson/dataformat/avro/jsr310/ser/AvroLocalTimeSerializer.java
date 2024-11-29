package tools.jackson.dataformat.avro.jsr310.ser;

import java.time.LocalTime;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializer for {@link LocalTime} into int value.
 *
 * Serialized value represents time of day, with no reference to a particular calendar,
 * time zone or date, where the int stores the number of milliseconds after midnight, 00:00:00.000.
 *
 * Note: In combination with {@link tools.jackson.dataformat.avro.schema.AvroSchemaGenerator#enableLogicalTypes()}
 * it aims to produce Avro schema with type int and logicalType time-millis:
 * {
 *   "type" : "int",
 *   "logicalType" : "time-millis"
 * }
 *
 * Serialization to string is not supported.
 */
public class AvroLocalTimeSerializer extends StdScalarSerializer<LocalTime>
{
    public static final AvroLocalTimeSerializer INSTANCE = new AvroLocalTimeSerializer();

    protected AvroLocalTimeSerializer() {
        super(LocalTime.class);
    }

    @Override
    public void serialize(LocalTime value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        /**
         * Number of milliseconds, with no reference to a particular calendar, time zone or date, after
         * midnight, 00:00:00.000.
         */
        long milliOfDay = value.toNanoOfDay() / 1000_000L;
        gen.writeNumber(milliOfDay);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JacksonException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.INT);
        }
    }

}
