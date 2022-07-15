package com.fasterxml.jackson.dataformat.ion;

import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

/**
 * {@link tools.jackson.core.TokenStreamFactory.TSFBuilder}
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
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected IonFactoryBuilder(boolean createBinary) {
        super(IonFactory.DEFAULT_ION_PARSER_FEATURE_FLAGS,
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

    public IonFactoryBuilder enable(IonParser.Feature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public IonFactoryBuilder enable(IonParser.Feature first, IonParser.Feature... other) {
        _formatReadFeatures |= first.getMask();
        for (IonParser.Feature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder disable(IonParser.Feature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public IonFactoryBuilder disable(IonParser.Feature first, IonParser.Feature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (IonParser.Feature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder configure(IonParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public IonFactoryBuilder enable(IonGenerator.Feature f) {
        _formatWriteFeatures |= f.getMask();
        return _this();
    }

    public IonFactoryBuilder enable(IonGenerator.Feature first, IonGenerator.Feature... other) {
        _formatWriteFeatures |= first.getMask();
        for (IonGenerator.Feature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder disable(IonGenerator.Feature f) {
        _formatWriteFeatures &= ~f.getMask();
        return _this();
    }

    public IonFactoryBuilder disable(IonGenerator.Feature first, IonGenerator.Feature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (IonGenerator.Feature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder configure(IonGenerator.Feature f, boolean state) {
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
