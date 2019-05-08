package com.fasterxml.jackson.dataformat.ion;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

import software.amazon.ion.IonSystem;
import software.amazon.ion.system.IonSystemBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link IonFactory} instances.
 *
 * @since 3.0
 */
public class IonFactoryBuilder extends DecorableTSFBuilder<IonFactory, IonFactoryBuilder>
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

    /**
     * Set of {@link IonFactory.Feature}s enabled, as bitmask.
     */
    protected boolean _createBinaryWriters;
    
    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    protected IonFactoryBuilder(boolean createBinary) {
        super(0, 0);
        _createBinaryWriters = createBinary;
    }

    public IonFactoryBuilder(IonFactory base) {
        super(base);
        _createBinaryWriters = base._cfgBinaryWriters;
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
