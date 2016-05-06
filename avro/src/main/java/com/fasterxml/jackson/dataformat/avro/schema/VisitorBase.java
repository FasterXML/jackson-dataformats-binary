package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWithSerializerProvider;

public abstract class VisitorBase
    implements JsonFormatVisitorWithSerializerProvider,
        SchemaBuilder
{
    protected SerializerProvider _provider;
    
    @Override
    public abstract Schema builtAvroSchema();

    @Override
    public SerializerProvider getProvider() {
        return _provider;
    }

    @Override
    public void setProvider(SerializerProvider provider) {
        _provider = provider;
    }
}
