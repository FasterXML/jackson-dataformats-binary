package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

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
     * Set of {@link FromXmlParser.Feature}s enabled, as bitmask.
     */
    protected int _formatParserFeatures;

    /**
     * Set of {@link ToXmlGenerator.Feature}s enabled, as bitmask.
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

    // // // Accessors

    public int formatParserFeaturesMask() { return _formatParserFeatures; }
    public int formatGeneratorFeaturesMask() { return _formatGeneratorFeatures; }

    @Override
    public AvroFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new AvroFactory(this);
    }
}
