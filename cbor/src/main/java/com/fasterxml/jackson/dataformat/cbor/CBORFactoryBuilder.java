package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link CBORFactory}
 * instances.
 *
 * @since 3.0
 */
public class CBORFactoryBuilder extends DecorableTSFBuilder<CBORFactory, CBORFactoryBuilder>
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Set of {@link CBORParser.Feature}s enabled, as bitmask.
     */
    protected int _formatParserFeatures;

    /**
     * Set of {@link CBORGenerator.Feature}s enabled, as bitmask.
     */
    protected int _formatGeneratorFeatures;

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected CBORFactoryBuilder() {
        _formatParserFeatures = CBORFactory.DEFAULT_CBOR_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = CBORFactory.DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS;
    }

    public CBORFactoryBuilder(CBORFactory base) {
        super(base);
        _formatParserFeatures = base._formatParserFeatures;
        _formatGeneratorFeatures = base._formatGeneratorFeatures;
    }

    // // // Parser features

    public CBORFactoryBuilder with(CBORParser.Feature f) {
        _formatParserFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder with(CBORParser.Feature first, CBORParser.Feature... other) {
        _formatParserFeatures |= first.getMask();
        for (CBORParser.Feature f : other) {
            _formatParserFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder without(CBORParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return _this();
    }

    public CBORFactoryBuilder without(CBORParser.Feature first, CBORParser.Feature... other) {
        _formatParserFeatures &= ~first.getMask();
        for (CBORParser.Feature f : other) {
            _formatParserFeatures &= ~f.getMask();
        }
        return _this();
    }

    // // // Generator features

    public CBORFactoryBuilder with(CBORGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder with(CBORGenerator.Feature first, CBORGenerator.Feature... other) {
        _formatGeneratorFeatures |= first.getMask();
        for (CBORGenerator.Feature f : other) {
            _formatGeneratorFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder without(CBORGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return _this();
    }
    
    public CBORFactoryBuilder without(CBORGenerator.Feature first, CBORGenerator.Feature... other) {
        _formatGeneratorFeatures &= ~first.getMask();
        for (CBORGenerator.Feature f : other) {
            _formatGeneratorFeatures &= ~f.getMask();
        }
        return _this();
    }
    
    // // // Accessors

    public int formatParserFeaturesMask() { return _formatParserFeatures; }
    public int formatGeneratorFeaturesMask() { return _formatGeneratorFeatures; }

    @Override
    public CBORFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new CBORFactory(this);
    }
}
