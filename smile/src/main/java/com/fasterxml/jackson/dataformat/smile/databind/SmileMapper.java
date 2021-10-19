package com.fasterxml.jackson.dataformat.smile.databind;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;

import com.fasterxml.jackson.dataformat.smile.PackageVersion;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

/**
 * Specialized {@link ObjectMapper} to use with CBOR format backend.
 *
 * @since 2.10
 */
public class SmileMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Smile backend.
     *
     * @since 2.10
     */
    public static class Builder extends MapperBuilder<SmileMapper, Builder>
    {
        protected final SmileFactory _streamFactory; // since 2.14

        public Builder(SmileMapper m) {
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
        public Builder enable(SmileParser.Feature... features) {
            for (SmileParser.Feature f : features) {
                _streamFactory.enable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder disable(SmileParser.Feature... features) {
            for (SmileParser.Feature f : features) {
                _streamFactory.disable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder configure(SmileParser.Feature f, boolean state)
        {
            if (state) {
                _streamFactory.enable(f);
            } else {
                _streamFactory.disable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder enable(SmileGenerator.Feature... features) {
            for (SmileGenerator.Feature f : features) {
                _streamFactory.enable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder disable(SmileGenerator.Feature... features) {
            for (SmileGenerator.Feature f : features) {
                _streamFactory.disable(f);
            }
            return this;
        }

        /**
         * @since 2.14
         */
        public Builder configure(SmileGenerator.Feature f, boolean state)
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

    public SmileMapper() {
        this(new SmileFactory());
    }

    public SmileMapper(SmileFactory f) {
        super(f);
    }

    protected SmileMapper(SmileMapper src) {
        super(src);
    }

    public static SmileMapper.Builder builder() {
        return new Builder(new SmileMapper());
    }

    public static Builder builder(SmileFactory streamFactory) {
        return new Builder(new SmileMapper(streamFactory));
    }

    @Override
    public SmileMapper copy()
    {
        _checkInvalidCopy(SmileMapper.class);
        return new SmileMapper(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public SmileFactory getFactory() {
        return (SmileFactory) _jsonFactory;
    }
}
