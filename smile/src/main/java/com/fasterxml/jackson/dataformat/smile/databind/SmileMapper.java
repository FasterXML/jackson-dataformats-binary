package com.fasterxml.jackson.dataformat.smile.databind;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperBuilderState;

import com.fasterxml.jackson.dataformat.smile.PackageVersion;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

/**
 * Specialized {@link ObjectMapper} to use with Smile format backend.
 */
public class SmileMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Smile backend.
     */
    public static class Builder extends MapperBuilder<SmileMapper, Builder>
    {
        public Builder(SmileFactory f) {
            super(f);
        }

        public Builder(StateImpl state) {
            super(state);
        }

        @Override
        public SmileMapper build() {
            return new SmileMapper(this);
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

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public SmileMapper() {
        this(new SmileFactory());
    }

    public SmileMapper(SmileFactory f) {
        this(new Builder(f));
    }

    public SmileMapper(Builder b) {
        super(b);
    }

    public static Builder builder() {
        return new Builder(new SmileFactory());
    }

    public static Builder builder(SmileFactory streamFactory) {
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
     * Accessor method for getting globally shared "default" {@link SmileMapper}
     * instance: one that has default configuration, no modules registered, no
     * config overrides. Usable mostly when dealing "untyped" or Tree-style
     * content reading and writing.
     */
    public static SmileMapper shared() {
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
    public SmileFactory tokenStreamFactory() {
        return (SmileFactory) _streamFactory;
    }

    /*
    /**********************************************************
    /* Helper class(es)
    /**********************************************************
     */

    /**
     * Helper class to contain dynamically constructed "shared" instance of
     * mapper, should one be needed via {@link #shared}.
     */
    private final static class SharedWrapper {
        private final static SmileMapper MAPPER = SmileMapper.builder().build();

        public static SmileMapper wrapped() { return MAPPER; }
    }
}
