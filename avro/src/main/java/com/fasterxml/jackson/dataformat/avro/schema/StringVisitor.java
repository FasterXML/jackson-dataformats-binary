package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.*;

import org.apache.avro.Schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;

public class StringVisitor extends JsonStringFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;
    protected final DefinedSchemas _schemas;

    protected Set<String> _enums;

    public StringVisitor(DefinedSchemas schemas, JavaType t) {
        _schemas = schemas;
        _type = t;
    }
    
    @Override
    public void format(JsonValueFormat format) {
        // Ideally, we'd recognize UUIDs, Dates etc if need be, here...
    }

    @Override
    public void enumTypes(Set<String> enums) {
        _enums = enums;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_enums == null) {
            return Schema.create(Schema.Type.STRING);
        }
        Schema s = Schema.createEnum(AvroSchemaHelper.getName(_type), "",
                AvroSchemaHelper.getNamespace(_type),
                new ArrayList<String>(_enums));
        _schemas.addSchema(_type, s);
        return s;
    }
}
