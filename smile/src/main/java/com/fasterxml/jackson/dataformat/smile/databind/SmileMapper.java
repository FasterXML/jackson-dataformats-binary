package com.fasterxml.jackson.dataformat.smile.databind;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.dataformat.smile.PackageVersion;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

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
     * CBOR backend.
     *
     * @since 2.10
     */
    public static class Builder extends MapperBuilder<SmileMapper, Builder>
    {
        public Builder(SmileMapper m) {
            super(m);
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

    @SuppressWarnings("unchecked")
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
