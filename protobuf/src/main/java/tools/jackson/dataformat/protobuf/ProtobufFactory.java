package tools.jackson.dataformat.protobuf;

import java.io.*;

import tools.jackson.core.*;
import tools.jackson.core.base.BinaryTSFactory;
import tools.jackson.core.io.IOContext;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.core.io.ContentReference;

public class ProtobufFactory
    extends BinaryTSFactory
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1;

    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_PROTOBUF_PARSER_FEATURE_FLAGS = ProtobufReadFeature.collectDefaults();

    /*
    /**********************************************************************
    /* Factory construction, configuration
    /**********************************************************************
     */

    public ProtobufFactory() {
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                DEFAULT_PROTOBUF_PARSER_FEATURE_FLAGS, 0);
    }

    protected ProtobufFactory(ProtobufFactory src) {
        super(src);
    }

    /**
     * Constructors used by {@link ProtobufFactoryBuilder} for instantiation.
     *
     * @since 3.0
     */
    protected ProtobufFactory(ProtobufFactoryBuilder b) {
        super(b);
    }

    @Override
    public ProtobufFactoryBuilder rebuild() {
        return new ProtobufFactoryBuilder(this);
    }

    /**
     * Main factory method to use for constructing {@link ProtobufFactory} instances with
     * different configuration.
     */
    public static ProtobufFactoryBuilder builder() {
        return new ProtobufFactoryBuilder();
    }

    @Override
    public ProtobufFactory copy(){
        return new ProtobufFactory(this);
    }

    /**
     * Instances are immutable so just return `this`
     */
    @Override
    public TokenStreamFactory snapshot() {
        return this;
    }

    /*
    /**********************************************************************
    /* Serializable overrides
    /**********************************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     */
    protected Object readResolve() {
        return new ProtobufFactory(this);
    }

    /*
    /**********************************************************************
    /* Basic introspection
    /**********************************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    // Protobuf is not positional
    @Override
    public boolean requiresPropertyOrdering() {
        return false;
    }

    @Override
    public boolean canParseAsync() {
        // 30-Sep-2017, tatu: No async implementation exists yet
        return false;
    }

    /*
    /**********************************************************************
    /* Format detection functionality
    /**********************************************************************
     */

    @Override
    public String getFormatName() {
        return ProtobufSchema.FORMAT_NAME_PROTOBUF;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof ProtobufSchema);
    }


    @Override
    public Class<ProtobufReadFeature> getFormatReadFeatureType() {
        return ProtobufReadFeature.class;
    }

/*
    @Override
    public Class<? extends FormatFeature> getFormatWriteFeatureType() {
        return null;
    }
*/
    @Override
    public int getFormatReadFeatures() { return _formatReadFeatures; }

    @Override
    public int getFormatWriteFeatures() { return 0; }

    /**
     * Check whether specified Protobuf-specific stream read feature is enabled.
     */
    public boolean isEnabled(ProtobufReadFeature f) {
        return f.enabledIn(_formatReadFeatures);
    }

    /*
    /**********************************************************************
    /* Factory methods: parsers
    /**********************************************************************
     */

    @Override
    protected IOContext _createContext(ContentReference contentRef, boolean resourceManaged) {
        return super._createContext(contentRef, resourceManaged);
    }

    @Override
    protected ProtobufParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in)
    {
        byte[] buf = ioCtxt.allocReadIOBuffer();
        return new ProtobufParser(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                (ProtobufSchema) readCtxt.getSchema(),
                in, buf, 0, 0, true);
    }

    @Override
    protected ProtobufParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len)
    {
        // [core#1548] Validate doc length up front for fixed buffers
        _streamReadConstraints.validateDocumentLength(len);
        return new ProtobufParser(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                (ProtobufSchema) readCtxt.getSchema(),
                null, data, offset, len, false);
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            DataInput input)
    {
        // 30-Sep-2017, tatu: As of now not supported although should be quite possible
        //    to support
        return _unsupported();
    }

    /*
    /**********************************************************************
    /* Factory methods: generators
    /**********************************************************************
     */

    @Override
    protected ProtobufGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IOContext ioCtxt, OutputStream out)
    {
        return new ProtobufGenerator(writeCtxt, ioCtxt,
                writeCtxt.getStreamWriteFeatures(_streamWriteFeatures),
                (ProtobufSchema) writeCtxt.getSchema(),
                out);
    }
}
