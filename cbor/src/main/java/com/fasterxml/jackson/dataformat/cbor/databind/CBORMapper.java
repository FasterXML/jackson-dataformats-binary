package com.fasterxml.jackson.dataformat.cbor.databind;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
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
        public Builder(CBORMapper m) {
            super(m);
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

    @SuppressWarnings("unchecked")
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
