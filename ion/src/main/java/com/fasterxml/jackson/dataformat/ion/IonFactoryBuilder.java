package com.fasterxml.jackson.dataformat.ion;

import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

import com.fasterxml.jackson.core.TSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TSFBuilder}
 * implementation for constructing {@link IonFactory} instances.
 */
public class IonFactoryBuilder extends TSFBuilder<IonFactory, IonFactoryBuilder>
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
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

    /**
     * Set of {@link IonParser.Feature}s enabled, as bitmask.
     *
     * @since 2.12
     */
    protected int _formatParserFeatures;

    /**
     * Set of {@link IonGenerator.Feature}s enabled, as bitmask.
     *
     * @since 2.12
     */
    protected int _formatGeneratorFeatures;

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected IonFactoryBuilder(boolean createBinary) {
        _createBinaryWriters = createBinary;
        _formatParserFeatures = IonFactory.DEFAULT_ION_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = IonFactory.DEFAULT_ION_GENERATOR_FEATURE_FLAGS;
    }

    public IonFactoryBuilder(IonFactory base) {
        super(base);
        _createBinaryWriters = base._cfgCreateBinaryWriters;
        _formatParserFeatures = base._ionParserFeatures;
        _formatGeneratorFeatures = base._ionGeneratorFeatures;
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
        _formatParserFeatures |= f.getMask();
        return _this();
    }

    public IonFactoryBuilder enable(IonParser.Feature first, IonParser.Feature... other) {
        _formatParserFeatures |= first.getMask();
        for (IonParser.Feature f : other) {
            _formatParserFeatures |= f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder disable(IonParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return _this();
    }

    public IonFactoryBuilder disable(IonParser.Feature first, IonParser.Feature... other) {
        _formatParserFeatures &= ~first.getMask();
        for (IonParser.Feature f : other) {
            _formatParserFeatures &= ~f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder configure(IonParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    // // // Generator features

    public IonFactoryBuilder enable(IonGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return _this();
    }

    public IonFactoryBuilder enable(IonGenerator.Feature first, IonGenerator.Feature... other) {
        _formatGeneratorFeatures |= first.getMask();
        for (IonGenerator.Feature f : other) {
            _formatGeneratorFeatures |= f.getMask();
        }
        return _this();
    }

    public IonFactoryBuilder disable(IonGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return _this();
    }

    public IonFactoryBuilder disable(IonGenerator.Feature first, IonGenerator.Feature... other) {
        _formatGeneratorFeatures &= ~first.getMask();
        for (IonGenerator.Feature f : other) {
            _formatGeneratorFeatures &= ~f.getMask();
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

    public int formatParserFeaturesMask() { return _formatParserFeatures; }
    public int formatGeneratorFeaturesMask() { return _formatGeneratorFeatures; }
}
