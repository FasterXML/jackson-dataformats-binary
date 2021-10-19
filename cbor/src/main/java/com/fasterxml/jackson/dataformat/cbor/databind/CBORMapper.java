package com.fasterxml.jackson.dataformat.cbor.databind;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
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
        protected final CBORFactory _streamFactory; // since 2.14

        public Builder(CBORMapper m) {
            super(m);
            _streamFactory = m.getFactory();
        }

        /*
        /******************************************************************
        /* Format features
        /******************************************************************
         */

        /**
         * @since 2.14
         */
        public Builder enable(CBORGenerator.Feature... features) {
            for (CBORGenerator.Feature f : features) {
                _streamFactory.enable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder disable(CBORGenerator.Feature... features) {
            for (CBORGenerator.Feature f : features) {
                _streamFactory.disable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder configure(CBORGenerator.Feature f, boolean state)
        {
            if (state) {
                _streamFactory.enable(f);
            } else {
                _streamFactory.disable(f);
            }
            return this;
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
        super(f);
    }

    protected CBORMapper(CBORMapper src) {
        super(src);
    }

    public static CBORMapper.Builder builder() {
        return new Builder(new CBORMapper());
    }

    public static Builder builder(CBORFactory streamFactory) {
        return new Builder(new CBORMapper(streamFactory));
    }

    @Override
    public CBORMapper copy()
    {
        _checkInvalidCopy(CBORMapper.class);
        return new CBORMapper(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public CBORFactory getFactory() {
        return (CBORFactory) _jsonFactory;
    }
    
}
