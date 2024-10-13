package tools.jackson.dataformat.avro.schema;

import java.util.*;

import org.apache.avro.Schema;

import tools.jackson.core.JsonParser.NumberType;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonValueFormat;

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
        // [dataformats-binary#179]: need special help with UUIDs, to coerce into Binary
        //   (could actually be
        if (_type.hasRawClass(java.util.UUID.class)) {
            return AvroSchemaHelper.createUUIDSchema();
        }
        AnnotatedClass annotations = _provider.introspectClassAnnotations(_type);
        Schema schema = Schema.create(Schema.Type.STRING);
        // Stringable classes need to include the type
        if (AvroSchemaHelper.isStringable(annotations) && !_type.hasRawClass(String.class)) {
            schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_type));
        }
        return schema;
    }
}
