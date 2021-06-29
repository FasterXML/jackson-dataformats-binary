package com.fasterxml.jackson.dataformat.avro.jsr310.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Serializer for {@link LocalDate} into int value.
 *
 * Serialized value represents number of days from the unix epoch, 1 January 1970 with no reference
 * to a particular time zone or time of day.
 *
 * Note: In combination with {@link com.fasterxml.jackson.dataformat.avro.schema.DateTimeVisitor} it aims to produce
 * Avro schema with type int and logicalType date:
 * {
 *   "type" : "int",
 *   "logicalType" : "date"
 * }
 *
 * Serialization to string is not supported.
 */
public class AvroLocalDateSerializer extends StdScalarSerializer<LocalDate> {

    private static final long serialVersionUID = 1L;

    public static final AvroLocalDateSerializer INSTANCE = new AvroLocalDateSerializer();

    protected AvroLocalDateSerializer() {
        super(LocalDate.class);
    }

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        /**
         * Number of days from the unix epoch, 1 January 1970.
         */
        gen.writeNumber(value.toEpochDay());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
        if (v2 != null) {
            v2.numberType(JsonParser.NumberType.INT);
        }
    }

}
