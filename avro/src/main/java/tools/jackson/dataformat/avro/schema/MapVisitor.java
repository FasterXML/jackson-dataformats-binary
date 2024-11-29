package tools.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import tools.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;

public class MapVisitor extends JsonMapFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final VisitorFormatWrapperImpl _visitorWrapper;

    protected Schema _valueSchema;

    protected JavaType _keyType;

    public MapVisitor(SerializationContext p, JavaType type, VisitorFormatWrapperImpl visitorWrapper)
    {
        super(p);
        _type = type;
        _visitorWrapper = visitorWrapper;
    }

    @Override
    public Schema builtAvroSchema() {
        // Assumption now is that we are done, so let's assign fields
        if (_valueSchema == null) {
            throw new IllegalStateException("Missing value type for "+_type);
        }
        AnnotatedClass ac = _provider.introspectClassAnnotations(_keyType);
        if (AvroSchemaHelper.isStringable(ac)) {
            return AvroSchemaHelper.stringableKeyMapSchema(_type, _keyType, _valueSchema);
        }
        throw new UnsupportedOperationException("Maps with non-stringable keys are not supported (yet?)");
    }

    /*
    /**********************************************************
    /* JsonMapFormatVisitor implementation
    /**********************************************************
     */

    @Override
    public void keyFormat(JsonFormatVisitable handler, JavaType keyType)
    {
        _keyType = keyType;
    }

    @Override
    public void valueFormat(JsonFormatVisitable handler, JavaType valueType)
    {
        VisitorFormatWrapperImpl visitorWrapper = _visitorWrapper.createChildWrapper();
        handler.acceptJsonFormatVisitor(visitorWrapper, valueType);
        _valueSchema = visitorWrapper.getAvroSchema();
    }
}
