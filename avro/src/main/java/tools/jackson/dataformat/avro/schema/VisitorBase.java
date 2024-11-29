package tools.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWithSerializationContext;

public abstract class VisitorBase
    implements JsonFormatVisitorWithSerializationContext,
        SchemaBuilder
{
    protected SerializationContext _provider;

    @Override
    public abstract Schema builtAvroSchema();

    @Override
    public SerializationContext getContext() {
        return _provider;
    }

    @Override
    public void setContext(SerializationContext provider) {
        _provider = provider;
    }
}
