package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.fasterxml.jackson.dataformat.protobuf.schemagen.ProtobufSchemaGenerator;

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

    /**
     * Accessor for reusable {@link ProtobufSchemaLoader} which can be
     * used for loading protoc definitions from files and other external
     * sources.
     */
    public ProtobufSchemaLoader schemaLoader() {
        return _schemaLoader;
    }

    public void setSchemaLoader(ProtobufSchemaLoader l) {
        _schemaLoader = l;
    }

    /**
     * Convenience method for constructing protoc definition that matches
     * given Java type. Uses {@link ProtobufSchemaGenerator} for
     * generation.
     *
     * @since 2.8
     */
    public ProtobufSchema generateSchemaFor(JavaType type) throws JsonMappingException
    {
        ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Convenience method for constructing protoc definition that matches
     * given Java type. Uses {@link ProtobufSchemaGenerator} for
     * generation.
     *
     * @since 2.8
     */
    public ProtobufSchema generateSchemaFor(Class<?> type) throws JsonMappingException
    {
        ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }
}
