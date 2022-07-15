package com.fasterxml.jackson.dataformat.smile;

import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link SmileFactory}
 * instances.
 */
public class SmileFactoryBuilder extends DecorableTSFBuilder<SmileFactory, SmileFactoryBuilder>
{
    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected SmileFactoryBuilder() {
        super(SmileFactory.DEFAULT_SMILE_PARSER_FEATURE_FLAGS,
                SmileFactory.DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS);
    }

    public SmileFactoryBuilder(SmileFactory base) {
        super(base);
    }

    @Override
    public SmileFactory build() {
        // No special settings beyond base class ones, so:
        return new SmileFactory(this);
    }

    /*
    /**********************************************************
    /* Configuration: on/off features
    /**********************************************************
     */

    // // // Parser features

    public SmileFactoryBuilder enable(SmileParser.Feature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public SmileFactoryBuilder enable(SmileParser.Feature first, SmileParser.Feature... other) {
        _formatReadFeatures |= first.getMask();
        for (SmileParser.Feature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder disable(SmileParser.Feature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public SmileFactoryBuilder disable(SmileParser.Feature first, SmileParser.Feature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (SmileParser.Feature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder configure(SmileParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public SmileFactoryBuilder enable(SmileGenerator.Feature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public SmileFactoryBuilder enable(SmileGenerator.Feature first, SmileGenerator.Feature... other) {
        _formatWriteFeatures |= first.getMask();
        for (SmileGenerator.Feature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder disable(SmileGenerator.Feature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }

    public SmileFactoryBuilder disable(SmileGenerator.Feature first, SmileGenerator.Feature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (SmileGenerator.Feature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder configure(SmileGenerator.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }
}
