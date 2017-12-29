package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link CBORFactory}
 * instances.
 *
 * @since 3.0
 */
public class CBORFactoryBuilder extends DecorableTSFBuilder<CBORFactory, CBORFactoryBuilder>
{
    public CBORFactoryBuilder() {
        super();
    }

    public CBORFactoryBuilder(CBORFactory base) {
        super(base);
    }

    @Override
    public CBORFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new CBORFactory(this);
    }
}
