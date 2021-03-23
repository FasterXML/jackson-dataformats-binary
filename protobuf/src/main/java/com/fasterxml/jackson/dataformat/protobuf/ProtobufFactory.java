package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;
import java.net.URL;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.InputSourceReference;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class ProtobufFactory extends JsonFactory
{
    private static final long serialVersionUID = 1;

    /*
    /**********************************************************
    /* Factory construction, configuration
    /**********************************************************
     */

    public ProtobufFactory() { }

    public ProtobufFactory(ObjectCodec codec) {
        super(codec);
    }

    protected ProtobufFactory(ProtobufFactory src, ObjectCodec oc)
    {
        super(src, oc);
    }

    /**
     * Constructors used by {@link ProtobufFactoryBuilder} for instantiation.
     *
     * @since 2.9
     */
    protected ProtobufFactory(ProtobufFactoryBuilder b) {
        super(b, false);
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
    public ProtobufFactory copy()
    {
        _checkInvalidCopy(ProtobufFactory.class);
        return new ProtobufFactory(this, null);
    }

    /*
    /**********************************************************
    /* Serializable overrides
    /**********************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     * Also: must be overridden by sub-classes as well.
     */
    @Override
    protected Object readResolve() {
        return new ProtobufFactory(this, _objectCodec);
    }

    /*                                                                                       
    /**********************************************************                              
    /* Versioned                                                                             
    /**********************************************************                              
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }
    
    /*
    /**********************************************************
    /* Format detection functionality
    /**********************************************************
     */
    
    @Override
    public String getFormatName() {
        return ProtobufSchema.FORMAT_NAME_PROTOBUF;
    }
    
    /**
     * Sub-classes need to override this method
     */
    @Override
    public MatchStrength hasFormat(InputAccessor acc) throws IOException
    {
        // TODO, if possible... probably isn't?
        return MatchStrength.INCONCLUSIVE;
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */

    // Protobuf is not positional
    @Override
    public boolean requiresPropertyOrdering() {
        return false;
    }

    // Protobuf can embed raw binary data natively
    @Override
    public boolean canHandleBinaryNatively() {
        return true;
    }

    @Override
    public boolean canUseCharArrays() { return false; }
    
    // No format-specific configuration, yet:
/*    
    @Override
    public Class<? extends FormatFeature> getFormatReadFeatureType() {
        return null;
    }

    @Override
    public Class<? extends FormatFeature> getFormatWriteFeatureType() {
        return null;
    }
*/

    /*
    /**********************************************************
    /* Overridden parser factory methods
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public ProtobufParser createParser(File f) throws IOException {
        final IOContext ctxt = _createContext(_createContentReference(f), true);
        return _createParser(_decorate(new FileInputStream(f), ctxt), ctxt);
    }

    @Override
    public ProtobufParser createParser(URL url) throws IOException {
        final IOContext ctxt = _createContext(_createContentReference(url), true);
        return _createParser(_decorate(_optimizedStreamFromURL(url), ctxt), ctxt);
    }

    @Override
    public ProtobufParser createParser(InputStream in) throws IOException {
        final IOContext ctxt = _createContext(_createContentReference(in), false);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @Override
    public ProtobufParser createParser(byte[] data) throws IOException {
        return _createParser(data, 0, data.length,
                _createContext(_createContentReference(data), true));
    }

    @SuppressWarnings("resource")
    @Override
    public ProtobufParser createParser(byte[] data, int offset, int len) throws IOException {
        IOContext ctxt = _createContext(_createContentReference(data, offset, len), true);
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, 0, data.length);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, offset, len, ctxt);
    }

    /*
    /**********************************************************
    /* Overridden generator factory methods
    /**********************************************************
     */

    @Override
    public ProtobufGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        IOContext ctxt = _createContext(_createContentReference(out), false);
        ctxt.setEncoding(enc);
        return _createProtobufGenerator(ctxt, _generatorFeatures, _objectCodec, _decorate(out, ctxt));
    }

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * protobuf-encoded output.
     *<p>
     * Since protobuf format always uses UTF-8 internally, no encoding need
     * to be passed to this method.
     */
    @Override
    public ProtobufGenerator createGenerator(OutputStream out) throws IOException {
        IOContext ctxt = _createContext(_createContentReference(out), false);
        return _createProtobufGenerator(ctxt, _generatorFeatures, _objectCodec, _decorate(out, ctxt));
    }

    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    @Override
    protected IOContext _createContext(InputSourceReference contentRef, boolean resourceManaged) {
        return super._createContext(contentRef, resourceManaged);
    }

    @Override
    protected ProtobufParser _createParser(InputStream in, IOContext ctxt) throws IOException
    {
        byte[] buf = ctxt.allocReadIOBuffer();
        return new ProtobufParser(ctxt, _parserFeatures,
                _objectCodec, in, buf, 0, 0, true);
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException {
        return _nonByteSource();
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt,
            boolean recyclable) throws IOException {
        return _nonByteSource();
    }

    @Override
    protected ProtobufParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException
    {
        return new ProtobufParser(ctxt, _parserFeatures,
                _objectCodec, null, data, offset, len, false);
    }

    @Override
    protected ProtobufGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        return _nonByteTarget();
    }

    @Override
    protected ProtobufGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        return _createProtobufGenerator(ctxt, _generatorFeatures, _objectCodec, out);
    }

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException {
        return _nonByteTarget();
    }

    private final ProtobufGenerator _createProtobufGenerator(IOContext ctxt,
            int stdFeat, ObjectCodec codec, OutputStream out) throws IOException
    {
        return new ProtobufGenerator(ctxt, stdFeat, _objectCodec, out);
    }
    
    protected <T> T _nonByteSource() {
        throw new UnsupportedOperationException("Can not create parser for non-byte-based source");
    }

    protected <T> T _nonByteTarget() {
        throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
    }
}
