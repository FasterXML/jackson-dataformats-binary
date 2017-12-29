package com.fasterxml.jackson.dataformat.ion;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link IonFactory}
 * instances.
 *
 * @since 3.0
 */
public class IonFactoryBuilder extends DecorableTSFBuilder<IonFactory, IonFactoryBuilder>
{
    public IonFactoryBuilder() {
        super();
    }

    public IonFactoryBuilder(IonFactory base) {
        super(base);
    }

    @Override
    public IonFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new IonFactory(this);
    }
}
