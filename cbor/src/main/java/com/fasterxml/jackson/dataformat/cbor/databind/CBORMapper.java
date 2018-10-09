package com.fasterxml.jackson.dataformat.cbor.databind;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperBuilderState;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.PackageVersion;

/**
 * Specialized {@link ObjectMapper} to use with CBOR format backend.
 *
 * @since 2.10
 */
public class CBORMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * CBOR backend.
     *
     * @since 2.10
     */
    public static class Builder extends MapperBuilder<CBORMapper, Builder>
    {
        public Builder(CBORFactory f) {
            super(f);
        }

        public Builder(StateImpl state) {
            super(state);
        }

        @Override
        public CBORMapper build() {
            return new CBORMapper(this);
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

    public CBORMapper() {
        this(new CBORFactory());
    }

    public CBORMapper(CBORFactory f) {
        this(new Builder(f));
    }

    public CBORMapper(Builder b) {
        super(b);
    }

    @SuppressWarnings("unchecked")
    public static Builder builder() {
        return new Builder(new CBORFactory());
    }

    public static Builder builder(CBORFactory streamFactory) {
        return new Builder(streamFactory);
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
    public CBORFactory tokenStreamFactory() {
        return (CBORFactory) _streamFactory;
    }
}
