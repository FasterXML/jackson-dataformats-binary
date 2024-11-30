package tools.jackson.dataformat.smile;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link tools.jackson.core.TSFBuilder}
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
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                SmileFactory.DEFAULT_SMILE_PARSER_FEATURE_FLAGS,
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

    public SmileFactoryBuilder enable(SmileReadFeature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public SmileFactoryBuilder enable(SmileReadFeature first, SmileReadFeature... other) {
        _formatReadFeatures |= first.getMask();
        for (SmileReadFeature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder disable(SmileReadFeature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public SmileFactoryBuilder disable(SmileReadFeature first, SmileReadFeature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (SmileReadFeature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder configure(SmileReadFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public SmileFactoryBuilder enable(SmileWriteFeature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public SmileFactoryBuilder enable(SmileWriteFeature first, SmileWriteFeature... other) {
        _formatWriteFeatures |= first.getMask();
        for (SmileWriteFeature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder disable(SmileWriteFeature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }

    public SmileFactoryBuilder disable(SmileWriteFeature first, SmileWriteFeature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (SmileWriteFeature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public SmileFactoryBuilder configure(SmileWriteFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }
}
