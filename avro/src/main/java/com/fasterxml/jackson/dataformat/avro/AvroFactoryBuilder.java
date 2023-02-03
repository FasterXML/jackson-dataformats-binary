package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.TSFBuilder;
import com.fasterxml.jackson.dataformat.avro.AvroFactoryBuilder;

/**
 * {@link com.fasterxml.jackson.core.TSFBuilder}
 * implementation for constructing {@link AvroFactory}
 * instances.
 *<p>
 * Note: one of standard features,
 * {@link com.fasterxml.jackson.core.JsonGenerator.Feature#AUTO_CLOSE_JSON_CONTENT},
 * is disabled by default, as it does not play well with error handling. It may be
 * forcibly enabled (if there is ever reason to do so), just defaults to {@code false}.
 *
 * @since 3.0
 */
public class AvroFactoryBuilder extends TSFBuilder<AvroFactory, AvroFactoryBuilder>
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

    /**
     * Flag that is set if Apache Avro lib's decoder is to be used for decoding;
     * `false` to use Jackson native Avro decoder.
     */
    protected boolean _useApacheLibDecoder;

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected AvroFactoryBuilder() {
        // default is to use native Jackson Avro decoder
        this(false);
    }

    protected AvroFactoryBuilder(boolean useApacheDecoder) {
        super();
        _formatParserFeatures = AvroFactory.DEFAULT_AVRO_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = AvroFactory.DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS;
        _useApacheLibDecoder = useApacheDecoder;

        // 04-Mar-2013, tatu: Content auto-closing is unfortunately a feature
        //    that works poorly with Avro error reporting, and generally
        //    manages to replace actual failure with a bogus one when
        //    missing "END_OBJECT"s (etc) are called. So let's default
        //    it to disabled, unlike for most JsonFactory sub-types.
        _streamWriteFeatures &= ~JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT.getMask();
    }

    public AvroFactoryBuilder(AvroFactory base) {
        super(base);
        _formatParserFeatures = base._avroParserFeatures;
        _formatGeneratorFeatures = base._avroGeneratorFeatures;
    }

    @Override
    public AvroFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new AvroFactory(this);
    }

    /*
    /**********************************************************
    /* Accessors
    /**********************************************************
     */

    public int formatParserFeaturesMask() { return _formatParserFeatures; }
    public int formatGeneratorFeaturesMask() { return _formatGeneratorFeatures; }

    public boolean useApacheLibDecoder() { return _useApacheLibDecoder; }

    /*
    /**********************************************************
    /* Mutators
    /**********************************************************
     */


    // // // Parser features

    public AvroFactoryBuilder enable(AvroParser.Feature f) {
        _formatParserFeatures |= f.getMask();
        return _this();
    }

    public AvroFactoryBuilder enable(AvroParser.Feature first, AvroParser.Feature... other) {
        _formatParserFeatures |= first.getMask();
        for (AvroParser.Feature f : other) {
            _formatParserFeatures |= f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder disable(AvroParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return _this();
    }

    public AvroFactoryBuilder disable(AvroParser.Feature first, AvroParser.Feature... other) {
        _formatParserFeatures &= ~first.getMask();
        for (AvroParser.Feature f : other) {
            _formatParserFeatures &= ~f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder configure(AvroParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public AvroFactoryBuilder enable(AvroGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return _this();
    }

    public AvroFactoryBuilder enable(AvroGenerator.Feature first, AvroGenerator.Feature... other) {
        _formatGeneratorFeatures |= first.getMask();
        for (AvroGenerator.Feature f : other) {
            _formatGeneratorFeatures |= f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder disable(AvroGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return _this();
    }

    public AvroFactoryBuilder disable(AvroGenerator.Feature first, AvroGenerator.Feature... other) {
        _formatGeneratorFeatures &= ~first.getMask();
        for (AvroGenerator.Feature f : other) {
            _formatGeneratorFeatures &= ~f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder configure(AvroGenerator.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }
}
