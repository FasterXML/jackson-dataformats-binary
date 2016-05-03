package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.net.URL;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;

/**
 * Factory used for constructing {@link CBORParser} and {@link CBORGenerator}
 * instances; both of which handle
 * <a href="https://www.rfc-editor.org/info/rfc7049">CBOR</a>
 * encoded data.
 *<p>
 * Extends {@link JsonFactory} mostly so that users can actually use it in place
 * of regular non-CBOR factory instances.
 *<p>
 * Note on using non-byte-based sources/targets (char based, like
 * {@link java.io.Reader} and {@link java.io.Writer}): these can not be
 * used for CBOR documents; attempt will throw exception.
 * 
 * @author Tatu Saloranta
 */
public class CBORFactory extends JsonFactory
{
	private static final long serialVersionUID = 1; // 2.6

    /*
    /**********************************************************
    /* Constants
    /**********************************************************
     */

	/**
     * Name used to identify CBOR format.
     * (and returned by {@link #getFormatName()}
     */
    public final static String FORMAT_NAME = "CBOR";
    
    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_CBOR_PARSER_FEATURE_FLAGS = CBORParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    final static int DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS = CBORGenerator.Feature.collectDefaults();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected int _formatParserFeatures;
    protected int _formatGeneratorFeatures;

    /*
    /**********************************************************
    /* Factory construction, configuration
    /**********************************************************
     */

    /**
     * Default constructor used to create factory instances.
     * Creation of a factory instance is a light-weight operation,
     * but it is still a good idea to reuse limited number of
     * factory instances (and quite often just a single instance):
     * factories are used as context for storing some reused
     * processing objects (such as symbol tables parsers use)
     * and this reuse only works within context of a single
     * factory instance.
     */
    public CBORFactory() { this(null); }

    public CBORFactory(ObjectCodec oc) {
        super(oc);
        _formatParserFeatures = DEFAULT_CBOR_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS;
    }

    /**
     * Note: REQUIRES at least 2.2.1 -- unfortunate intra-patch dep but seems
     * preferable to just leaving bug be as is
     * 
     * @since 2.2.1
     */
    public CBORFactory(CBORFactory src, ObjectCodec oc)
    {
        super(src, oc);
        _formatParserFeatures = src._formatParserFeatures;
        _formatGeneratorFeatures = src._formatGeneratorFeatures;
    }

    @Override
    public CBORFactory copy()
    {
        _checkInvalidCopy(CBORFactory.class);
        // note: as with base class, must NOT copy mapper reference
        return new CBORFactory(this, null);
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
        return new CBORFactory(this, _objectCodec);
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
        return FORMAT_NAME;
    }

    // Defaults work fine for this:
    // public boolean canUseSchema(FormatSchema schema) { }

    @Override
    public MatchStrength hasFormat(InputAccessor acc) throws IOException {
        return CBORParserBootstrapper.hasCBORFormat(acc);
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */

    @Override
    public boolean canHandleBinaryNatively() {
        return true;
    }

    @Override // since 2.6
    public Class<CBORParser.Feature> getFormatReadFeatureType() {
        return CBORParser.Feature.class;
    }

    @Override // since 2.6
    public Class<CBORGenerator.Feature> getFormatWriteFeatureType() {
        return CBORGenerator.Feature.class;
    }
    
    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link CBORParser.Feature} for list of features)
     */
    public final CBORFactory configure(CBORParser.Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for enabling specified parser feature
     * (check {@link CBORParser.Feature} for list of features)
     */
    public CBORFactory enable(CBORParser.Feature f) {
        _formatParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link CBORParser.Feature} for list of features)
     */
    public CBORFactory disable(CBORParser.Feature f) {
        _formatParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(CBORParser.Feature f) {
        return (_formatParserFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Configuration, generator settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link CBORGenerator.Feature} for list of features)
     */
    public final CBORFactory configure(CBORGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link CBORGenerator.Feature} for list of features)
     */
    public CBORFactory enable(CBORGenerator.Feature f) {
        _formatGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link CBORGenerator.Feature} for list of features)
     */
    public CBORFactory disable(CBORGenerator.Feature f) {
        _formatGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(CBORGenerator.Feature f) {
        return (_formatGeneratorFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Overridden parser factory methods, new (2.1)
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public CBORParser createParser(File f) throws IOException {
        return _createParser(new FileInputStream(f), _createContext(f, true));
    }

    @Override
    public CBORParser createParser(URL url) throws IOException {
        return _createParser(_optimizedStreamFromURL(url), _createContext(url, true));
    }

    @Override
    public CBORParser createParser(InputStream in) throws IOException {
        return _createParser(in, _createContext(in, false));
    }

    @Override
    public CBORParser createParser(byte[] data) throws IOException {
        return _createParser(data, 0, data.length, _createContext(data, true));
    }

    @Override
    public CBORParser createParser(byte[] data, int offset, int len) throws IOException {
        return _createParser(data, offset, len, _createContext(data, true));
    }

    /*
    /**********************************************************
    /* Overridden generator factory methods
    /**********************************************************
     */

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * CBOR-encoded output.
     *<p>
     * Since CBOR format always uses UTF-8 internally, <code>enc</code>
     * argument is ignored.
     */
    @Override
    public CBORGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        return _createCBORGenerator(_createContext(out, false),
                _generatorFeatures, _formatGeneratorFeatures, _objectCodec, out);
    }

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * CBOR-encoded output.
     *<p>
     * Since CBOR format always uses UTF-8 internally, no encoding need
     * to be passed to this method.
     */
    @Override
    public CBORGenerator createGenerator(OutputStream out) throws IOException {
        return _createCBORGenerator(_createContext(out, false),
                _generatorFeatures, _formatGeneratorFeatures, _objectCodec, out);
    }

    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    @Override
    protected IOContext _createContext(Object srcRef, boolean resourceManaged) {
        return super._createContext(srcRef, resourceManaged);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected CBORParser _createParser(InputStream in, IOContext ctxt) throws IOException
    {
        return new CBORParserBootstrapper(ctxt, in).constructParser(_factoryFeatures,
                _parserFeatures, _formatParserFeatures,
                _objectCodec, _byteSymbolCanonicalizer);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException {
        return _nonByteSource();
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt,
            boolean recyclable) throws IOException {
        return _nonByteSource();
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected CBORParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException
    {
        return new CBORParserBootstrapper(ctxt, data, offset, len).constructParser(
                _factoryFeatures, _parserFeatures, _formatParserFeatures,
                _objectCodec, _byteSymbolCanonicalizer);
    }

    @Override
    protected CBORGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        return _nonByteTarget();
    }

    @Override
    protected CBORGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        return _createCBORGenerator(ctxt,
                _generatorFeatures, _formatGeneratorFeatures, _objectCodec, out);
    }

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException {
        return _nonByteTarget();
    }

    private final CBORGenerator _createCBORGenerator(IOContext ctxt,
            int stdFeat, int formatFeat, ObjectCodec codec, OutputStream out) throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        CBORGenerator gen = new CBORGenerator(ctxt, stdFeat, formatFeat, _objectCodec, out);
        if (CBORGenerator.Feature.WRITE_TYPE_HEADER.enabledIn(formatFeat)) {
            gen.writeTag(CBORConstants.TAG_ID_SELF_DESCRIBE);
        }
        return gen;
    }
    
    protected <T> T _nonByteTarget() {
        throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
    }

    protected <T> T _nonByteSource() {
        throw new UnsupportedOperationException("Can not create generator for non-byte-based source");
    }
}
