package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class ProtobufMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    protected ProtobufSchemaLoader _schemaLoader = ProtobufSchemaLoader.std;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public ProtobufMapper() {
        this(new ProtobufFactory());
    }

    public ProtobufMapper(ProtobufFactory f) {
        super(f);
    }

    protected ProtobufMapper(ProtobufMapper src) {
        super(src);
    }
    
    @Override
    public ProtobufMapper copy()
    {
        _checkInvalidCopy(ProtobufMapper.class);
        return new ProtobufMapper(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public ProtobufFactory getFactory() {
        return (ProtobufFactory) _jsonFactory;
    }

    /*
    /**********************************************************
    /* Schema access
    /**********************************************************
     */

    public ProtobufSchemaLoader schemaLoader() {
        return _schemaLoader;
    }

    public void setSchemaLoader(ProtobufSchemaLoader l) {
        _schemaLoader = l;
    }
}
