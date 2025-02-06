package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.Set;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;

public class StringVisitor extends JsonStringFormatVisitor.Base
    implements SchemaBuilder
{
    protected final SerializerProvider _provider;
    protected final JavaType _type;

    public StringVisitor(SerializerProvider provider, JavaType type) {
        _type = type;
        _provider = provider;
    }

    @Override
    public void format(JsonValueFormat format) {
        // Ideally, we'd recognize UUIDs, Dates etc if need be, here...
    }

    @Override
    public void enumTypes(Set<String> enums) {
    	// Do nothing
    }

    @Override
    public Schema builtAvroSchema() {
        // Unlike Jackson, Avro treats characters as an int with the java.lang.Character class type.
        if (_type.hasRawClass(char.class) || _type.hasRawClass(Character.class)) {
            // should we construct JavaType for `Character.class` in case of primitive or... ?
            return AvroSchemaHelper.numericAvroSchema(NumberType.INT, _type);
        }

        BeanDescription bean = _provider.getConfig().introspectClassAnnotations(_type);
        Schema schema = Schema.create(Schema.Type.STRING);
        // Stringable classes need to include the type
        if (AvroSchemaHelper.isStringable(bean.getClassInfo()) && !_type.hasRawClass(String.class)) {
            schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_type));
        }
        return schema;
    }
}
