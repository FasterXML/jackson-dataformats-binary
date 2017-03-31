package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.ArrayList;
import java.util.Set;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class StringVisitor extends JsonStringFormatVisitor.Base
    implements SchemaBuilder
{
    protected final SerializerProvider _provider;
    protected final JavaType _type;
    protected final DefinedSchemas _schemas;

    protected Set<String> _enums;

    public StringVisitor(SerializerProvider provider, DefinedSchemas schemas, JavaType t) {
        _schemas = schemas;
        _type = t;
        _provider = provider;
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
        // Unlike Jackson, Avro treats characters as an int with the java.lang.Character class type.
        if (_type.hasRawClass(char.class) || _type.hasRawClass(Character.class)) {
            return AvroSchemaHelper.numericAvroSchema(NumberType.INT, TypeFactory.defaultInstance().constructType(Character.class));
        }
        BeanDescription bean = _provider.getConfig().introspectClassAnnotations(_type);
        if (_enums != null) {
            Schema s = AvroSchemaHelper.createEnumSchema(bean, new ArrayList<>(_enums));
            _schemas.addSchema(_type, s);
            return s;
        }
        Schema schema = Schema.create(Schema.Type.STRING);
        // Stringable classes need to include the type
        if (AvroSchemaHelper.isStringable(bean.getClassInfo()) && !_type.hasRawClass(String.class)) {
            schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_type));
        }
        return schema;
    }
}
