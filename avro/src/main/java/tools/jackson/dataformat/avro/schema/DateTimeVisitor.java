package tools.jackson.dataformat.avro.schema;

import java.time.*;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

import tools.jackson.core.JsonParser;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;

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
            String logicalType = getLogicalType(schema.getType(), _hint);

            if (logicalType != null) {
                schema.addProp(LogicalType.LOGICAL_TYPE_PROP, logicalType);
            } else {
                schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_hint));
            }
        }
        return schema;
    }

    private String getLogicalType(Schema.Type avroType, JavaType hint) {
        Class<?> clazz = hint.getRawClass();

        if (OffsetDateTime.class.isAssignableFrom(clazz) && Schema.Type.LONG == avroType) {
            return TIMESTAMP_MILLIS;
        }
        if (ZonedDateTime.class.isAssignableFrom(clazz) && Schema.Type.LONG == avroType) {
            return TIMESTAMP_MILLIS;
        }
        if (Instant.class.isAssignableFrom(clazz) && Schema.Type.LONG == avroType) {
            return TIMESTAMP_MILLIS;
        }

        if (LocalDate.class.isAssignableFrom(clazz) && Schema.Type.INT == avroType) {
            return DATE;
        }
        if (LocalTime.class.isAssignableFrom(clazz) && Schema.Type.INT == avroType) {
            return TIME_MILLIS;
        }
        if (LocalDateTime.class.isAssignableFrom(clazz) && Schema.Type.LONG == avroType) {
            return LOCAL_TIMESTAMP_MILLIS;
        }

        return null;
    }

    private static final String DATE = "date";
    private static final String TIME_MILLIS = "time-millis";
    private static final String TIMESTAMP_MILLIS = "timestamp-millis";
    private static final String LOCAL_TIMESTAMP_MILLIS = "local-timestamp-millis";

}
