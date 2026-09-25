package tools.jackson.dataformat.protobuf;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link tools.jackson.core.TSFBuilder}
 * implementation for constructing {@link ProtobufFactory}
 * instances.
 *
 * @since 3.0
 */
public class ProtobufFactoryBuilder extends DecorableTSFBuilder<ProtobufFactory, ProtobufFactoryBuilder>
{
    public ProtobufFactoryBuilder() {
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                ProtobufFactory.DEFAULT_PROTOBUF_PARSER_FEATURE_FLAGS, 0);
    }

    public ProtobufFactoryBuilder(ProtobufFactory base) {
        super(base);
    }

    // // // Parser features

    public ProtobufFactoryBuilder enable(ProtobufReadFeature f) {
        _formatReadFeatures |= f.getMask();
        return _this();
    }

    public ProtobufFactoryBuilder enable(ProtobufReadFeature first, ProtobufReadFeature... other) {
        _formatReadFeatures |= first.getMask();
        for (ProtobufReadFeature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return _this();
    }

    public ProtobufFactoryBuilder disable(ProtobufReadFeature f) {
        _formatReadFeatures &= ~f.getMask();
        return _this();
    }

    public ProtobufFactoryBuilder disable(ProtobufReadFeature first, ProtobufReadFeature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (ProtobufReadFeature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return _this();
    }

    public ProtobufFactoryBuilder configure(ProtobufReadFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    @Override
    public ProtobufFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new ProtobufFactory(this);
    }
}
