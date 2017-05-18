package com.fasterxml.jackson.dataformat.smile;

import java.io.*;
import java.net.URL;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.dataformat.smile.async.NonBlockingByteArrayParser;

/**
 * Factory used for constructing {@link SmileParser} and {@link SmileGenerator}
 * instances; both of which handle
 * <a href="http://wiki.fasterxml.com/SmileFormat">Smile</a> encoded data.
 *<p>
 * Extends {@link JsonFactory} mostly so that users can actually use it in place
 * of regular non-Smile factory instances.
 *<p>
 * Note on using non-byte-based sources/targets (char based, like
 * {@link java.io.Reader} and {@link java.io.Writer}): these can not be
 * used for Smile-format documents, and thus will either downgrade to
 * textual JSON (when parsing), or throw exception (when trying to create
 * generator).
 * 
 * @author Tatu Saloranta
 */
public class SmileFactory extends JsonFactory
{
    private static final long serialVersionUID = 1L; // since 2.6

    /*
    /**********************************************************
    /* Constants
    /**********************************************************
     */
    
    /**
     * Name used to identify Smile format.
     * (and returned by {@link #getFormatName()}
     */
    public final static String FORMAT_NAME_SMILE = "Smile";
    
    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_SMILE_PARSER_FEATURE_FLAGS = SmileParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    final static int DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS = SmileGenerator.Feature.collectDefaults();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Whether non-supported methods (ones trying to output using
     * char-based targets like {@link java.io.Writer}, for example)
     * should be delegated to regular Jackson JSON processing
     * (if set to true); or throw {@link UnsupportedOperationException}
     * (if set to false)
     */
    protected boolean _cfgDelegateToTextual;

    protected int _smileParserFeatures;
    protected int _smileGeneratorFeatures;

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
    public SmileFactory() { this(null); }

    public SmileFactory(ObjectCodec oc) {
        super(oc);
        _smileParserFeatures = DEFAULT_SMILE_PARSER_FEATURE_FLAGS;
        _smileGeneratorFeatures = DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS;
    }

    /**
     * Note: REQUIRES 2.2.1 -- unfortunate intra-patch dep but seems
     * preferable to just leaving bug be as is
     * 
     * @since 2.2.1
     */
    public SmileFactory(SmileFactory src, ObjectCodec oc)
    {
        super(src, oc);
        _cfgDelegateToTextual = src._cfgDelegateToTextual;
        _smileParserFeatures = src._smileParserFeatures;
        _smileGeneratorFeatures = src._smileGeneratorFeatures;
    }

    // @since 2.1
    @Override
    public SmileFactory copy()
    {
        _checkInvalidCopy(SmileFactory.class);
        // note: as with base class, must NOT copy mapper reference
        return new SmileFactory(this, null);
    }
    
    public void delegateToTextual(boolean state) {
        _cfgDelegateToTextual = state;
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
        return new SmileFactory(this, _objectCodec);
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
        return FORMAT_NAME_SMILE;
    }

    // Defaults work fine for this:
    // public boolean canUseSchema(FormatSchema schema) { }

    /**
     * Sub-classes need to override this method (as of 1.8)
     */
    @Override
    public MatchStrength hasFormat(InputAccessor acc) throws IOException {
        return SmileParserBootstrapper.hasSmileFormat(acc);
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */

    @Override
    public boolean canUseCharArrays() { return false; }

    @Override
    public boolean canHandleBinaryNatively() { return true; }

    @Override // since 2.9
    public boolean canParseAsync() { return true; }
    
    @Override // since 2.6
    public Class<SmileParser.Feature> getFormatReadFeatureType() {
        return SmileParser.Feature.class;
    }

    @Override // since 2.6
    public Class<SmileGenerator.Feature> getFormatWriteFeatureType() {
        return SmileGenerator.Feature.class;
    }

    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link SmileParser.Feature} for list of features)
     */
    public final SmileFactory configure(SmileParser.Feature f, boolean state)
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
     * (check {@link SmileParser.Feature} for list of features)
     */
    public SmileFactory enable(SmileParser.Feature f) {
        _smileParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link SmileParser.Feature} for list of features)
     */
    public SmileFactory disable(SmileParser.Feature f) {
        _smileParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(SmileParser.Feature f) {
        return (_smileParserFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Configuration, generator settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link SmileGenerator.Feature} for list of features)
     *
     * @since 1.2
     */
    public final SmileFactory configure(SmileGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link SmileGenerator.Feature} for list of features)
     */
    public SmileFactory enable(SmileGenerator.Feature f) {
        _smileGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link SmileGenerator.Feature} for list of features)
     */
    public SmileFactory disable(SmileGenerator.Feature f) {
        _smileGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(SmileGenerator.Feature f) {
        return (_smileGeneratorFeatures & f.getMask()) != 0;
    }
    
    /*
    /**********************************************************
    /* Overridden parser factory methods: only override methods
    /* that can use co-variance (to return SmileParser)
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public SmileParser createParser(File f) throws IOException {
        IOContext ctxt = _createContext(f, true);
        return _createParser(_decorate(new FileInputStream(f), ctxt), ctxt);
    }

    @Override
    public SmileParser createParser(URL url) throws IOException {
        IOContext ctxt = _createContext(url, true);
        return _createParser(_decorate(_optimizedStreamFromURL(url), ctxt), ctxt);
    }

    @Override
    public SmileParser createParser(InputStream in) throws IOException {
        IOContext ctxt = _createContext(in, false);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @Override
    public SmileParser createParser(byte[] data) throws IOException {
        return createParser(data, 0, data.length);
    }
    
    @SuppressWarnings("resource")
    @Override
    public SmileParser createParser(byte[] data, int offset, int len) throws IOException {
        IOContext ctxt = _createContext(data, true);
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, 0, data.length);
            if (in != null) {
                return _createParser(_decorate(in, ctxt), ctxt);
            }
        }
        return _createParser(data, offset, len, ctxt);
    }

    /*
    /**********************************************************
    /* Overridden generator factory methods: mostly
    /* overridden for co-variance (returns SmileGenerator)
    /**********************************************************
     */

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * Smile-encoded output.
     *<p>
     * Since Smile format always uses UTF-8 internally, <code>enc</code>
     * argument is ignored.
     */
    @Override
    public SmileGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(out, false);
        return _createGenerator(_decorate(out, ctxt), ctxt);
    }

    /**
     * Method for constructing {@link JsonGenerator} for generating
     * Smile-encoded output.
     *<p>
     * Since Smile format always uses UTF-8 internally, no encoding need
     * to be passed to this method.
     */
    @Override
    public SmileGenerator createGenerator(OutputStream out) throws IOException {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(out, false);
        return _createGenerator(_decorate(out, ctxt), ctxt);
    }

    /*
    /**********************************************************
    /* Experimental extended factory method(s) for creating
    /* non-blocking parsers
    /**********************************************************
     */

    /**
     * @since 2.9
     */
    @Override
    public NonBlockingByteArrayParser createNonBlockingByteArrayParser() throws IOException {
        IOContext ctxt = _createContext(null, false);
        ByteQuadsCanonicalizer can = _byteSymbolCanonicalizer.makeChild(_factoryFeatures);
        return new NonBlockingByteArrayParser(ctxt, _parserFeatures, _smileParserFeatures, can);
    }

    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    /**
     * Overridable factory method that actually instantiates desired parser.
     */
    @Override
    protected SmileParser _createParser(InputStream in, IOContext ctxt) throws IOException
    {
        SmileParserBootstrapper bs = new SmileParserBootstrapper(ctxt, in);
        return bs.constructParser(_factoryFeatures, _parserFeatures,
        		_smileParserFeatures, _objectCodec, _byteSymbolCanonicalizer);
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException
    {
        if (_cfgDelegateToTextual) {
            return super._createParser(r, ctxt);
        }
        return _nonByteSource();
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt,
            boolean recyclable) throws IOException
    {
        if (_cfgDelegateToTextual) {
            return super._createParser(data, offset, len, ctxt, recyclable);
        }
        return _nonByteSource();
    }

    @Override
    protected SmileParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException
    {
        return new SmileParserBootstrapper(ctxt, data, offset, len).constructParser(
                _factoryFeatures, _parserFeatures, _smileParserFeatures,
                _objectCodec, _byteSymbolCanonicalizer);
    }

    @Override
    protected JsonGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException
    {
        if (_cfgDelegateToTextual) {
            return super._createGenerator(out, ctxt);
        }
        return _nonByteTarget(); 
    }

    @Override
    protected JsonGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        return _createGenerator(out, ctxt);
    }
    
    //public BufferRecycler _getBufferRecycler()

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException
    {
        if (_cfgDelegateToTextual) {
            return super._createWriter(out, enc, ctxt);
        }
        return _nonByteTarget(); 
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected <T> T _nonByteSource() throws IOException {
        throw new UnsupportedOperationException("Can not create parser for character-based (not byte-based) source");
    }

    protected <T> T _nonByteTarget() throws IOException {
        throw new UnsupportedOperationException("Can not create generator for character-based (not byte-based) target");
    }
    
    protected SmileGenerator _createGenerator(OutputStream out, IOContext ctxt) throws IOException
    {
        int feats = _smileGeneratorFeatures;
        /* One sanity check: MUST write header if shared string values setting is enabled,
         * or quoting of binary data disabled.
         * But should we force writing, or throw exception, if settings are in conflict?
         * For now, let's error out...
         */
        SmileGenerator gen = new SmileGenerator(ctxt, _generatorFeatures, feats, _objectCodec, out);
        if ((feats & SmileGenerator.Feature.WRITE_HEADER.getMask()) != 0) {
            gen.writeHeader();
        } else {
            if ((feats & SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES.getMask()) != 0) {
                throw new JsonGenerationException(
                        "Inconsistent settings: WRITE_HEADER disabled, but CHECK_SHARED_STRING_VALUES enabled; can not construct generator"
                        +" due to possible data loss (either enable WRITE_HEADER, or disable CHECK_SHARED_STRING_VALUES to resolve)",
                        gen);
            }
            if ((feats & SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT.getMask()) == 0) {
                throw new JsonGenerationException(
        			"Inconsistent settings: WRITE_HEADER disabled, but ENCODE_BINARY_AS_7BIT disabled; can not construct generator"
        			+" due to possible data loss (either enable WRITE_HEADER, or ENCODE_BINARY_AS_7BIT to resolve)",
        			gen);
            }
        }
        return gen;
    }
}
