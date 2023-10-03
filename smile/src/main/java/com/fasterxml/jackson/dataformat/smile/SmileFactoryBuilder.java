package com.fasterxml.jackson.dataformat.smile;

import java.util.Objects;

import com.fasterxml.jackson.core.TSFBuilder;
import com.fasterxml.jackson.core.util.RecyclerPool;

/**
 * {@link com.fasterxml.jackson.core.TSFBuilder}
 * implementation for constructing {@link SmileFactory}
 * instances.
 *
 * @since 3.0
 */
public class SmileFactoryBuilder extends TSFBuilder<SmileFactory, SmileFactoryBuilder>
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * @since 2.16
     */
    protected RecyclerPool<SmileBufferRecycler> _smileRecyclerPool;
    
    /**
     * Set of {@link SmileParser.Feature}s enabled, as bitmask.
     */
    protected int _formatParserFeatures;

    /**
     * Set of {@link SmileGenerator.Feature}s enabled, as bitmask.
     */
    protected int _formatGeneratorFeatures;

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected SmileFactoryBuilder() {
        _smileRecyclerPool = SmileBufferRecyclers.defaultPool();
        _formatParserFeatures = SmileFactory.DEFAULT_SMILE_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = SmileFactory.DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS;
    }

    public SmileFactoryBuilder(SmileFactory base) {
        super(base);
        _smileRecyclerPool = base._smileRecyclerPool;
        _formatParserFeatures = base._smileParserFeatures;
        _formatGeneratorFeatures = base._smileGeneratorFeatures;
    }

    @Override
    public SmileFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new SmileFactory(this);
    }

    // // // Parser features

    public SmileFactoryBuilder enable(SmileParser.Feature f) {
        _formatParserFeatures |= f.getMask();
        return _this();
    }

    public SmileFactoryBuilder enable(SmileParser.Feature first, SmileParser.Feature... other) {
        _formatParserFeatures |= first.getMask();
        for (SmileParser.Feature f : other) {
            _formatParserFeatures |= f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder disable(SmileParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return _this();
    }

    public SmileFactoryBuilder disable(SmileParser.Feature first, SmileParser.Feature... other) {
        _formatParserFeatures &= ~first.getMask();
        for (SmileParser.Feature f : other) {
            _formatParserFeatures &= ~f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder configure(SmileParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public SmileFactoryBuilder enable(SmileGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return _this();
    }

    public SmileFactoryBuilder enable(SmileGenerator.Feature first, SmileGenerator.Feature... other) {
        _formatGeneratorFeatures |= first.getMask();
        for (SmileGenerator.Feature f : other) {
            _formatGeneratorFeatures |= f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder disable(SmileGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return _this();
    }

    public SmileFactoryBuilder disable(SmileGenerator.Feature first, SmileGenerator.Feature... other) {
        _formatGeneratorFeatures &= ~first.getMask();
        for (SmileGenerator.Feature f : other) {
            _formatGeneratorFeatures &= ~f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder configure(SmileGenerator.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Other configuration

    /**
     * @param p RecyclerPool to use for buffer allocation
     *
     * @return this builder (for call chaining)
     *
     * @since 2.16
     */
    public SmileFactoryBuilder smileRcyclerPool(RecyclerPool<SmileBufferRecycler> p) {
        _smileRecyclerPool = Objects.requireNonNull(p);
        return _this();
    }
    
    // // // Accessors

    // @since 2.16
    public RecyclerPool<SmileBufferRecycler> smileRecyclerPool() {
        return _smileRecyclerPool;
    }

    public int formatParserFeaturesMask() { return _formatParserFeatures; }
    public int formatGeneratorFeaturesMask() { return _formatGeneratorFeatures; }
}
