package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;

public class DoubleVisitor
    extends JsonNumberFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _hint;
    protected JsonParser.NumberType _type;

    public DoubleVisitor(JavaType typeHint) {
        _hint = typeHint;
    }

    @Override
    public void numberType(JsonParser.NumberType type) {
        _type = type;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_type == null) {
            // 16-Mar-2016, tatu: if no known numeric type assume "Number", which
            //    would require union most likely
            return AvroSchemaHelper.anyNumberSchema();
        }
        return AvroSchemaHelper.numericAvroSchema(_type, _hint);
    }
}
