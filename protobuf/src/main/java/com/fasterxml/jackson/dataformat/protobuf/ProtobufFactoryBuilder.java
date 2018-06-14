package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.TSFBuilder;

/**
 * {@link com.fasterxml.jackson.core.TSFBuilder}
 * implementation for constructing {@link ProtobufFactory}
 * instances.
 *
 * @since 3.0
 */
public class ProtobufFactoryBuilder extends TSFBuilder<ProtobufFactory, ProtobufFactoryBuilder>
{
    public ProtobufFactoryBuilder() {
        super();
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
