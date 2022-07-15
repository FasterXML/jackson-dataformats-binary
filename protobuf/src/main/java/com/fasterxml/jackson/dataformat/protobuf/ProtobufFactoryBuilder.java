package com.fasterxml.jackson.dataformat.protobuf;

import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TokenStreamFactory.TSFBuilder}
 * implementation for constructing {@link ProtobufFactory}
 * instances.
 *
 * @since 3.0
 */
public class ProtobufFactoryBuilder extends DecorableTSFBuilder<ProtobufFactory, ProtobufFactoryBuilder>
{
    public ProtobufFactoryBuilder() {
        super(0, 0);
    }

    public ProtobufFactoryBuilder(ProtobufFactory base) {
        super(base);
    }

    @Override
    public ProtobufFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new ProtobufFactory(this);
    }
}
