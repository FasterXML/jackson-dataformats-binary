package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.TSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TSFBuilder}
 * implementation for constructing {@link CBORFactory}
 * instances.
 *
 * @since 3.0
 */
public class CBORFactoryBuilder extends TSFBuilder<CBORFactory, CBORFactoryBuilder>
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
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
    /**********************************************************************
    /* Life cycle
    /**********************************************************************
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

    public CBORFactoryBuilder enable(CBORParser.Feature f) {
        _formatParserFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder enable(CBORParser.Feature first, CBORParser.Feature... other) {
        _formatParserFeatures |= first.getMask();
        for (CBORParser.Feature f : other) {
            _formatParserFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder disable(CBORParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return _this();
    }

    public CBORFactoryBuilder disable(CBORParser.Feature first, CBORParser.Feature... other) {
        _formatParserFeatures &= ~first.getMask();
        for (CBORParser.Feature f : other) {
            _formatParserFeatures &= ~f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder configure(CBORParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public CBORFactoryBuilder enable(CBORGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder enable(CBORGenerator.Feature first, CBORGenerator.Feature... other) {
        _formatGeneratorFeatures |= first.getMask();
        for (CBORGenerator.Feature f : other) {
            _formatGeneratorFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder disable(CBORGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return _this();
    }

    public CBORFactoryBuilder disable(CBORGenerator.Feature first, CBORGenerator.Feature... other) {
        _formatGeneratorFeatures &= ~first.getMask();
        for (CBORGenerator.Feature f : other) {
            _formatGeneratorFeatures &= ~f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder configure(CBORGenerator.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
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
