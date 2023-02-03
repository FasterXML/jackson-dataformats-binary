package tools.jackson.dataformat.cbor;

import tools.jackson.core.StreamReadConstraints;
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
                CBORFactory.DEFAULT_CBOR_PARSER_FEATURE_FLAGS,
                CBORFactory.DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS);
    }

    public CBORFactoryBuilder(CBORFactory base) {
        super(base);
    }

    // // // Parser features

    public CBORFactoryBuilder enable(CBORParser.Feature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder enable(CBORParser.Feature first, CBORParser.Feature... other) {
        _formatReadFeatures |= first.getMask();
        for (CBORParser.Feature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder disable(CBORParser.Feature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public CBORFactoryBuilder disable(CBORParser.Feature first, CBORParser.Feature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (CBORParser.Feature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder configure(CBORParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public CBORFactoryBuilder enable(CBORGenerator.Feature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public CBORFactoryBuilder enable(CBORGenerator.Feature first, CBORGenerator.Feature... other) {
        _formatWriteFeatures |= first.getMask();
        for (CBORGenerator.Feature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder disable(CBORGenerator.Feature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }

    public CBORFactoryBuilder disable(CBORGenerator.Feature first, CBORGenerator.Feature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (CBORGenerator.Feature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public CBORFactoryBuilder configure(CBORGenerator.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    @Override
    public CBORFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new CBORFactory(this);
    }
}
