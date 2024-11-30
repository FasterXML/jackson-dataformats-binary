package tools.jackson.dataformat.ion;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

/**
 * {@link tools.jackson.core.TSFBuilder}
 * implementation for constructing {@link IonFactory} instances.
 */
public class IonFactoryBuilder extends DecorableTSFBuilder<IonFactory, IonFactoryBuilder>
{
    /*
    /***********************************************************************
    /* Configuration
    /***********************************************************************
     */

    /**
     * If a custom {@link IonSystem} instance is needed, configured here;
     * if left as {@code null}, will use:
     *{@code
     *  IonSystemBuilder.standard().build()
     *}
     */
    protected IonSystem _system;

    protected boolean _createBinaryWriters;

    /*
    /***********************************************************************
    /* Life cycle
    /***********************************************************************
     */

    protected IonFactoryBuilder(boolean createBinary) {
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                IonFactory.DEFAULT_ION_PARSER_FEATURE_FLAGS,
                IonFactory.DEFAULT_ION_GENERATOR_FEATURE_FLAGS);
        _createBinaryWriters = createBinary;
    }

    public IonFactoryBuilder(IonFactory base) {
        super(base);
        _createBinaryWriters = base._cfgBinaryWriters;
    }

    @Override
    public IonFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new IonFactory(this);
    }

    /*
    /**********************************************************
    /* Configuration: Ion-specific
    /**********************************************************
     */

    public IonFactoryBuilder withBinaryWriters() {
        _createBinaryWriters = true;
        return this;
    }

    public IonFactoryBuilder withTextualWriters() {
        _createBinaryWriters = false;
        return this;
    }

    public IonFactoryBuilder ionSystem(IonSystem system) {
        _system = system;
        return this;
    }

    /*
    /**********************************************************
    /* Configuration: on/off features
    /**********************************************************
     */

    // // // Parser features

    public IonFactoryBuilder enable(IonReadFeature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public IonFactoryBuilder enable(IonReadFeature first, IonReadFeature... other) {
        _formatReadFeatures |= first.getMask();
        for (IonReadFeature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder disable(IonReadFeature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public IonFactoryBuilder disable(IonReadFeature first, IonReadFeature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (IonReadFeature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder configure(IonReadFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public IonFactoryBuilder enable(IonWriteFeature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public IonFactoryBuilder enable(IonWriteFeature first, IonWriteFeature... other) {
        _formatWriteFeatures |= first.getMask();
        for (IonWriteFeature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder disable(IonWriteFeature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }

    public IonFactoryBuilder disable(IonWriteFeature first, IonWriteFeature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (IonWriteFeature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder configure(IonWriteFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    /*
    /**********************************************************
    /* Accessors
    /**********************************************************
     */

    public boolean willCreateBinaryWriters() { return _createBinaryWriters; }

    public IonSystem ionSystem() {
        if (_system == null) {
            return IonSystemBuilder.standard().build();
        }
        return _system;
    }
}
