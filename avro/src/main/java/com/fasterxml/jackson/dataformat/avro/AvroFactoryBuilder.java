package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;
import com.fasterxml.jackson.dataformat.avro.AvroFactoryBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link AvroFactory}
 * instances.
 *
 * @since 3.0
 */
public class AvroFactoryBuilder extends DecorableTSFBuilder<AvroFactory, AvroFactoryBuilder>
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Set of {@link AvroParser.Feature}s enabled, as bitmask.
     */
    protected int _formatParserFeatures;

    /**
     * Set of {@link AvroGenerator.Feature}s enabled, as bitmask.
     */
    protected int _formatGeneratorFeatures;

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected AvroFactoryBuilder() {
        _formatParserFeatures = AvroFactory.DEFAULT_AVRO_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = AvroFactory.DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS;
    }

    public AvroFactoryBuilder(AvroFactory base) {
        super(base);
        _formatParserFeatures = base._formatParserFeatures;
        _formatGeneratorFeatures = base._formatGeneratorFeatures;
    }

    // // // Parser features

    public AvroFactoryBuilder with(AvroParser.Feature f) {
        _formatParserFeatures |= f.getMask();
        return _this();
    }

    public AvroFactoryBuilder with(AvroParser.Feature first, AvroParser.Feature... other) {
        _formatParserFeatures |= first.getMask();
        for (AvroParser.Feature f : other) {
            _formatParserFeatures |= f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder without(AvroParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return _this();
    }

    public AvroFactoryBuilder without(AvroParser.Feature first, AvroParser.Feature... other) {
        _formatParserFeatures &= ~first.getMask();
        for (AvroParser.Feature f : other) {
            _formatParserFeatures &= ~f.getMask();
        }
        return _this();
    }

    // // // Generator features

    public AvroFactoryBuilder with(AvroGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return _this();
    }

    public AvroFactoryBuilder with(AvroGenerator.Feature first, AvroGenerator.Feature... other) {
        _formatGeneratorFeatures |= first.getMask();
        for (AvroGenerator.Feature f : other) {
            _formatGeneratorFeatures |= f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder without(AvroGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return _this();
    }
    
    public AvroFactoryBuilder without(AvroGenerator.Feature first, AvroGenerator.Feature... other) {
        _formatGeneratorFeatures &= ~first.getMask();
        for (AvroGenerator.Feature f : other) {
            _formatGeneratorFeatures &= ~f.getMask();
        }
        return _this();
    }

    // // // Accessors

    public int formatParserFeaturesMask() { return _formatParserFeatures; }
    public int formatGeneratorFeaturesMask() { return _formatGeneratorFeatures; }

    @Override
    public AvroFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new AvroFactory(this);
    }
}
