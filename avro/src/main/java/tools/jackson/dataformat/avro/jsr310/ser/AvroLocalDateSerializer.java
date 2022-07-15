package tools.jackson.dataformat.avro.jsr310.ser;

import java.time.LocalDate;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializer for {@link LocalDate} into int value.
 *
 * Serialized value represents number of days from the unix epoch, 1 January 1970 with no reference
 * to a particular time zone or time of day.
 *
 * Note: In combination with {@link tools.jackson.dataformat.avro.schema.AvroSchemaGenerator#enableLogicalTypes()}
 * it aims to produce Avro schema with type int and logicalType date:
 * {
 *   "type" : "int",
 *   "logicalType" : "date"
 * }
 *
 * Serialization to string is not supported.
 */
public class AvroLocalDateSerializer extends StdScalarSerializer<LocalDate>
{
    public static final AvroLocalDateSerializer INSTANCE = new AvroLocalDateSerializer();

    protected AvroLocalDateSerializer() {
        super(LocalDate.class);
    }

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws JacksonException {
        /**
         * Number of days from the unix epoch, 1 January 1970.
         */
        gen.writeNumber(value.toEpochDay());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JacksonException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.INT);
        }
    }

}
