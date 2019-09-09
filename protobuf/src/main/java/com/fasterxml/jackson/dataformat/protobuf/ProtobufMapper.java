package com.fasterxml.jackson.dataformat.protobuf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperBuilderState;

import com.fasterxml.jackson.dataformat.protobuf.schema.DescriptorLoader;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.fasterxml.jackson.dataformat.protobuf.schemagen.ProtobufSchemaGenerator;

public class ProtobufMapper extends ObjectMapper
{
    private static final long serialVersionUID = 3L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Protobuf backend.
     *
     * @since 3.0
     */
    public static class Builder extends MapperBuilder<ProtobufMapper, Builder>
    {
        public Builder(ProtobufFactory f) {
            super(f);
        }

        public Builder(StateImpl state) {
            super(state);
        }

        @Override
        public ProtobufMapper build() {
            return new ProtobufMapper(this);
        }

        @Override
        protected MapperBuilderState _saveState() {
            return new StateImpl(this);
        }

        protected static class StateImpl extends MapperBuilderState
            implements java.io.Serializable // important!
        {
            private static final long serialVersionUID = 3L;
    
            public StateImpl(Builder src) {
                super(src);
            }
    
            // We also need actual instance of state as base class can not implement logic
             // for reinstating mapper (via mapper builder) from state.
            @Override
            protected Object readResolve() {
                return new Builder(this).build();
            }
        }
    }

    protected ProtobufSchemaLoader _schemaLoader = ProtobufSchemaLoader.std;

    /**
     * Lazily constructed instance of {@link DescriptorLoader}, used for loading
     * structured protoc definitions from multiple files.
     */
    protected DescriptorLoader _descriptorLoader;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public ProtobufMapper() {
        this(new ProtobufFactory());
    }

    public ProtobufMapper(ProtobufFactory f) {
        this(new Builder(f));
    }

    public ProtobufMapper(Builder b) {
        super(b);
    }

    public static Builder builder() {
        return new Builder(new ProtobufFactory());
    }

    public static Builder builder(ProtobufFactory streamFactory) {
        return new Builder(streamFactory);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder rebuild() {
        return new Builder((Builder.StateImpl) _savedBuilderState);
    }

    /*
    /**********************************************************************
    /* Life-cycle, shared "vanilla" (default configuration) instance
    /**********************************************************************
     */

    /**
     * Accessor method for getting globally shared "default" {@link ProtobufMapper}
     * instance: one that has default configuration, no modules registered, no
     * config overrides. Usable mostly when dealing "untyped" or Tree-style
     * content reading and writing.
     */
    public static ProtobufMapper shared() {
        return SharedWrapper.wrapped();
    }

    /*
    /**********************************************************************
    /* Basic accessor overrides
    /**********************************************************************
     */
    
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public ProtobufFactory tokenStreamFactory() {
        return (ProtobufFactory) _streamFactory;
    }

    /*
    /**********************************************************************
    /* Schema access, single protoc source
    /**********************************************************************
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
     * @param type Resolved type to generate {@link ProtobufSchema} for
     *
     * @return Generated {@link ProtobufSchema}
     */
    public ProtobufSchema generateSchemaFor(JavaType type) throws JsonMappingException
    {
        ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Convenience method for constructing protoc definition that matches
     * given Java type. Uses {@link ProtobufSchemaGenerator} for generation.
     *
     * @param type Type-erased type to generate {@link ProtobufSchema} for
     *
     * @return Generated {@link ProtobufSchema}
     */
    public ProtobufSchema generateSchemaFor(Class<?> type) throws JsonMappingException
    {
        ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Convenience method for constructing protoc definition that matches
     * given Java type. Uses {@link ProtobufSchemaGenerator} for generation.
     *
     * @param type Type to generate {@link ProtobufSchema} for
     *
     * @return Generated {@link ProtobufSchema}
     */
    public ProtobufSchema generateSchemaFor(TypeReference<?> type) throws JsonMappingException {
        return generateSchemaFor(_typeFactory.constructType(type));
    }

    /*
    /**********************************************************************
    /* Schema access, FileDescriptorSets
    /**********************************************************************
     */

    public FileDescriptorSet loadDescriptorSet(URL src) throws IOException {
        return descriptorLoader().load(src);
    }

    public FileDescriptorSet loadDescriptorSet(File src) throws IOException {
        return descriptorLoader().load(src);
    }

    public FileDescriptorSet loadDescriptorSet(InputStream src) throws IOException {
        return descriptorLoader().load(src);
    }

    /**
     * Accessors that may be used instead of convenience <code>loadDescriptorSet</code>
     * methods, if alternate sources need to be used.
     */
    public synchronized DescriptorLoader descriptorLoader() throws IOException
    {
        DescriptorLoader l = _descriptorLoader;
        if (l == null) {
            _descriptorLoader = l = DescriptorLoader.construct(this);
        }
        return l;
    }

    /*
    /**********************************************************************
    /* Helper class(es)
    /**********************************************************************
     */

    /**
     * Helper class to contain dynamically constructed "shared" instance of
     * mapper, should one be needed via {@link #shared}.
     */
    private final static class SharedWrapper {
        private final static ProtobufMapper MAPPER = ProtobufMapper.builder().build();

        public static ProtobufMapper wrapped() { return MAPPER; }
    }
}
