package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;

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
        Schema schema = AvroSchemaHelper.numericAvroSchema(_type);
        if (_hint != null) {
            schema.addProp(SpecificData.CLASS_PROP, _hint.toCanonical());
        }
        return schema;
    }
}
