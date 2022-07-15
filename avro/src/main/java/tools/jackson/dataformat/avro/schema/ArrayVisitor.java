package tools.jackson.dataformat.avro.schema;

import static tools.jackson.dataformat.avro.schema.AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS;

import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;

public class ArrayVisitor
    extends JsonArrayFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final VisitorFormatWrapperImpl _visitorWrapper;

    protected Schema _elementSchema;

    public ArrayVisitor(SerializerProvider p, JavaType type, VisitorFormatWrapperImpl visitorWrapper)
    {
        super(p);
        _type = type;
        _visitorWrapper = visitorWrapper;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_elementSchema == null) {
            throw new IllegalStateException("No element schema created for: "+_type);
        }
        Schema schema = Schema.createArray(_elementSchema);
        if (!_type.hasRawClass(List.class)) {
            schema.addProp(AVRO_SCHEMA_PROP_CLASS, AvroSchemaHelper.getTypeId(_type));
        }
        return schema;
    }

    /*
    /**********************************************************
    /* JsonArrayFormatVisitor implementation
    /**********************************************************
     */

    @Override
    public void itemsFormat(JsonFormatVisitable visitable, JavaType type)
    {
        VisitorFormatWrapperImpl wrapper = _visitorWrapper.createChildWrapper();
        visitable.acceptJsonFormatVisitor(wrapper, type);
        _elementSchema = wrapper.getAvroSchema();
    }

    @Override
    public void itemsFormat(JsonFormatTypes type)
    {
        // Unlike Jackson, Avro treats character arrays as an array of ints with the java.lang.Character class type.
        if (_type.hasRawClass(char[].class)) {
            _elementSchema = AvroSchemaHelper.typedSchema(Type.INT, _type.getContentType());
        } else {
            _elementSchema = AvroSchemaHelper.simpleSchema(type, _type.getContentType());
        }
    }
}
