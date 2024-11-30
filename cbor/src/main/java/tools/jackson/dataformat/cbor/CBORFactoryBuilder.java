package tools.jackson.dataformat.cbor;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link tools.jackson.core.TSFBuilder}
 * implementation for constructing {@link CBORFactory}
 * instances.
 *
 * @since 3.0
 */
public class CBORFactoryBuilder extends DecorableTSFBuilder<CBORFactory, CBORFactoryBuilder>
{
    /*
    /**********************************************************************
    /* Life cycle
    /**********************************************************************
     */

    protected CBORFactoryBuilder() {
        super(StreamReadConstraints.defaults(),
                StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                0,
                CBORFactory.DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS);
    }

    public CBORFactoryBuilder(CBORFactory base) {
        super(base);
    }

    // // // Generator features

    public CBORFactoryBuilder enable(CBORWriteFeature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder enable(CBORWriteFeature first, CBORWriteFeature... other) {
        _formatWriteFeatures |= first.getMask();
        for (CBORWriteFeature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder disable(CBORWriteFeature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }

    public CBORFactoryBuilder disable(CBORWriteFeature first, CBORWriteFeature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (CBORWriteFeature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder configure(CBORWriteFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    @Override
    public CBORFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new CBORFactory(this);
    }
}
