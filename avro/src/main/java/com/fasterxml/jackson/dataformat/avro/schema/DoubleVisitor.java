package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;

public class DoubleVisitor
    extends JsonNumberFormatVisitor.Base
    implements SchemaBuilder
{
    protected JsonParser.NumberType _type;

    public DoubleVisitor() { }

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
        return AvroSchemaHelper.numericAvroSchema(_type);
    }
}
