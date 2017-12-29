package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link AvroFactory}
 * instances.
 *
 * @since 3.0
 */
public class AvroFactoryBuilder extends DecorableTSFBuilder<AvroFactory, AvroFactoryBuilder>
{
    public AvroFactoryBuilder() {
        super();
    }

    public AvroFactoryBuilder(AvroFactory base) {
        super(base);
    }

    @Override
    public AvroFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new AvroFactory(this);
    }
}
