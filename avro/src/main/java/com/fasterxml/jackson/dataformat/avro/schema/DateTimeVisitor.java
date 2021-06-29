package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class DateTimeVisitor extends JsonIntegerFormatVisitor.Base
        implements SchemaBuilder {

    protected JsonParser.NumberType _type;
    protected JavaType _hint;

    public DateTimeVisitor() {
    }

    public DateTimeVisitor(JavaType typeHint) {
        _hint = typeHint;
    }

    @Override
    public void numberType(JsonParser.NumberType type) {
        _type = type;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_type == null) {
            throw new IllegalStateException("No number type indicated");
        }

        Schema schema = AvroSchemaHelper.numericAvroSchema(_type);
        if (_hint != null) {
            String logicalType = logicalType(_hint);
            if (logicalType != null) {
                schema.addProp(LogicalType.LOGICAL_TYPE_PROP, logicalType);
            } else {
                schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_hint));
            }
        }
        return schema;
    }

    private String logicalType(JavaType hint) {
        Class<?> clazz = hint.getRawClass();

        if (OffsetDateTime.class.isAssignableFrom(clazz)) {
            return TIMESTAMP_MILLIS;
        }
        if (ZonedDateTime.class.isAssignableFrom(clazz)) {
            return TIMESTAMP_MILLIS;
        }
        if (Instant.class.isAssignableFrom(clazz)) {
            return TIMESTAMP_MILLIS;
        }

        if (LocalDate.class.isAssignableFrom(clazz)) {
            return DATE;
        }
        if (LocalTime.class.isAssignableFrom(clazz)) {
            return TIME_MILLIS;
        }
        if (LocalDateTime.class.isAssignableFrom(clazz)) {
            return LOCAL_TIMESTAMP_MILLIS;
        }

        return null;
    }

    private static final String DATE = "date";
    private static final String TIME_MILLIS = "time-millis";
    private static final String TIMESTAMP_MILLIS = "timestamp-millis";
    private static final String LOCAL_TIMESTAMP_MILLIS = "local-timestamp-millis";

}
