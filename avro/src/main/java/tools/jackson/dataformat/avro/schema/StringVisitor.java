package tools.jackson.dataformat.avro.schema;

import java.util.ArrayList;
import java.util.Set;

import org.apache.avro.Schema;

import tools.jackson.core.JsonParser.NumberType;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonValueFormat;
import tools.jackson.databind.type.TypeFactory;

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
        // [dataformats-binary#179]: need special help with UUIDs, to coerce into Binary
        //   (could actually be 
        if (_type.hasRawClass(java.util.UUID.class)) {
            return AvroSchemaHelper.createUUIDSchema();
        }
        AnnotatedClass annotations = _provider.introspectClassAnnotations(_type);
        if (_enums != null) {
            Schema s = AvroSchemaHelper.createEnumSchema(_provider.getConfig(), _type,
                    annotations, new ArrayList<>(_enums));
            _schemas.addSchema(_type, s);
            return s;
        }
        Schema schema = Schema.create(Schema.Type.STRING);
        // Stringable classes need to include the type
        if (AvroSchemaHelper.isStringable(annotations) && !_type.hasRawClass(String.class)) {
            schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_type));
        }
        return schema;
    }
}
