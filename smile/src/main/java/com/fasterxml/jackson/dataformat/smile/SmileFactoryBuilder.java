package com.fasterxml.jackson.dataformat.smile;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link SmileFactory}
 * instances.
 *
 * @since 3.0
 */
public class SmileFactoryBuilder extends DecorableTSFBuilder<SmileFactory, SmileFactoryBuilder>
{
    public SmileFactoryBuilder() {
        super();
    }

    public SmileFactoryBuilder(SmileFactory base) {
        super(base);
    }

    @Override
    protected SmileFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new SmileFactory(this);
    }
}
