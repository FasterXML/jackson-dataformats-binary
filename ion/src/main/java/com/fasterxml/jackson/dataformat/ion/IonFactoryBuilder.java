package com.fasterxml.jackson.dataformat.ion;

import com.fasterxml.jackson.core.TSFBuilder;

import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

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

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected IonFactoryBuilder(boolean createBinary) {
        _createBinaryWriters = createBinary;
    }

    public IonFactoryBuilder(IonFactory base) {
        super(base);
        _createBinaryWriters = base._cfgCreateBinaryWriters;
    }

    // // // Configuration

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

    @Override
    public IonFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new IonFactory(this);
    }
}
