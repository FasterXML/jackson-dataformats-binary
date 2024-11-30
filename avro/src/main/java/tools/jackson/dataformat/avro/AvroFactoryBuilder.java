package tools.jackson.dataformat.avro;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;
import tools.jackson.dataformat.avro.AvroFactoryBuilder;

/**
 * {@link tools.jackson.core.TSFBuilder}
 * implementation for constructing {@link AvroFactory}
 * instances.
 *<p>
 * Note: one of standard features, {@link StreamWriteFeature#AUTO_CLOSE_CONTENT},
 * is disabled by default, as it does not play well with error handling. It may be
 * forcibly enabled (if there is ever reason to do so), just defaults to {@code false}.
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
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                AvroFactory.DEFAULT_AVRO_PARSER_FEATURE_FLAGS,
                AvroFactory.DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS);
        _useApacheLibDecoder = useApacheDecoder;

        // 04-Mar-2013, tatu: Content auto-closing is unfortunately a feature
        //    that works poorly with Avro error reporting, and generally
        //    manages to replace actual failure with a bogus one when
        //    missing "END_OBJECT"s (etc) are called. So let's default
        //    it to disabled, unlike for most JsonFactory sub-types.
        _streamWriteFeatures &= ~StreamWriteFeature.AUTO_CLOSE_CONTENT.getMask();
    }

    public AvroFactoryBuilder(AvroFactory base) {
        super(base);
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

    public boolean useApacheLibDecoder() { return _useApacheLibDecoder; }

    /*
    /**********************************************************
    /* Mutators
    /**********************************************************
     */

    // // // Parser features

    public AvroFactoryBuilder enable(AvroReadFeature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public AvroFactoryBuilder enable(AvroReadFeature first, AvroReadFeature... other) {
        _formatReadFeatures |= first.getMask();
        for (AvroReadFeature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder disable(AvroReadFeature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public AvroFactoryBuilder disable(AvroReadFeature first, AvroReadFeature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (AvroReadFeature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder configure(AvroReadFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public AvroFactoryBuilder enable(AvroWriteFeature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public AvroFactoryBuilder enable(AvroWriteFeature first, AvroWriteFeature... other) {
        _formatWriteFeatures |= first.getMask();
        for (AvroWriteFeature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder disable(AvroWriteFeature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }
    
    public AvroFactoryBuilder disable(AvroWriteFeature first, AvroWriteFeature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (AvroWriteFeature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public AvroFactoryBuilder configure(AvroWriteFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }
}
