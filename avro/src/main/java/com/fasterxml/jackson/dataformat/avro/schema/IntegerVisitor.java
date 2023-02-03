package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;

public class IntegerVisitor extends JsonIntegerFormatVisitor.Base
    implements SchemaBuilder
{
    protected JsonParser.NumberType _type;
    protected JavaType _hint;

    public IntegerVisitor() {}

    public IntegerVisitor(JavaType typeHint) {
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
        return AvroSchemaHelper.numericAvroSchema(_type, _hint);
    }
}
