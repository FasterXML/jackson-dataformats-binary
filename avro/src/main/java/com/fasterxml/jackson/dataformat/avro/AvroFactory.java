package com.fasterxml.jackson.dataformat.avro;

import java.io.*;
import java.net.URL;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;

import com.fasterxml.jackson.dataformat.avro.deser.*;

/**
 * Default {@link JsonFactory} implementation for encoding/decoding Avro
 * content, uses native Jackson encoder/decoder.
 *
 * @see com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory
 */
public class AvroFactory extends JsonFactory
{
    private static final long serialVersionUID = 1L;

    public final static String FORMAT_NAME_AVRO = "avro";

    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_AVRO_PARSER_FEATURE_FLAGS = AvroParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    final static int DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS = AvroGenerator.Feature.collectDefaults();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected int _avroParserFeatures;

    protected int _avroGeneratorFeatures;

    /**
     * Flag that is set if Apache Avro lib's decoder is to be used for decoding;
     * `false` to use Jackson native Avro decoder.
     *
     * @since 2.9
     */
    protected boolean _useApacheLibDecoder;

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
    public AvroFactory() {
        // 09-Jan-2017, tatu: We must actually create and pass builder to be able to change
        //    one of JsonGenerator.Features (See builder for details)
        super(new AvroFactoryBuilder(), false);
        _avroParserFeatures = DEFAULT_AVRO_PARSER_FEATURE_FLAGS;
        _avroGeneratorFeatures = DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS;
        _useApacheLibDecoder = false;
    }

    public AvroFactory(ObjectCodec oc)
    {
        super(oc);
        _avroParserFeatures = DEFAULT_AVRO_PARSER_FEATURE_FLAGS;
        _avroGeneratorFeatures = DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS;
        _useApacheLibDecoder = false;

        /* 04-Mar-2013, tatu: Content auto-closing is unfortunately a feature
         *    that works poorly with Avro error reporting, and generally
         *    manages to replace actual failure with a bogus one when
         *    missing "END_OBJECT"s (etc) are called. So let's default
         *    it to disabled, unlike for most JsonFactory sub-types.
         */
        disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);
    }

    protected AvroFactory(AvroFactory src, ObjectCodec oc)
    {
        super(src, oc);
        _avroParserFeatures = src._avroParserFeatures;
        _avroGeneratorFeatures = src._avroGeneratorFeatures;
        _useApacheLibDecoder = src._useApacheLibDecoder;
    }

    /**
     * Constructors used by {@link AvroFactoryBuilder} for instantiation.
     *
     * @since 2.9
     */
    protected AvroFactory(AvroFactoryBuilder b)
    {
        super(b, false);
        _avroParserFeatures = b.formatParserFeaturesMask();
        _avroGeneratorFeatures = b.formatGeneratorFeaturesMask();
        _useApacheLibDecoder = b.useApacheLibDecoder();
    }

    @Override
    public AvroFactoryBuilder rebuild() {
        return new AvroFactoryBuilder(this);
    }

    /**
     * Main factory method to use for constructing a builder for creating
     * {@link AvroFactory} instances with different configuration.
     * Builder is initialized to defaults and this is equivalent to calling
     * {@link #builderWithNativeDecoder}.
     */
    public static AvroFactoryBuilder builder() {
        return new AvroFactoryBuilder();
    }

    /**
     * Main factory method to use for constructing a builder for creating
     * {@link AvroFactory} instances with different configuration,
     * initialized to use Apache Avro library codec for decoding content
     * (instead of Jackson native decoder).
     */
    public static AvroFactoryBuilder builderWithApacheDecoder() {
        return new AvroFactoryBuilder(true);
    }

    /**
     * Main factory method to use for constructing a builder for creating
     * {@link AvroFactory} instances with different configuration,
     * initialized to use Jackson antive codec for decoding content
     * (instead of Apache Avro library decoder).
     */
    public static AvroFactoryBuilder builderWithNativeDecoder() {
        return new AvroFactoryBuilder(false);
    }

    @Override
    public AvroFactory copy()
    {
        _checkInvalidCopy(AvroFactory.class);
        return new AvroFactory(this, null);
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */

    // Yes, Avro is strictly positional based on schema
    @Override
    public boolean requiresPropertyOrdering() {
        return true;
    }

    @Override // since 2.10 (should have been earlier)
    public boolean canHandleBinaryNatively() {
        return true;
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
        return new AvroFactory(this, _objectCodec);
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
        return FORMAT_NAME_AVRO;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof AvroSchema);
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
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link AvroParser.Feature} for list of features)
     */
    public final AvroFactory configure(AvroParser.Feature f, boolean state)
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
     * (check {@link AvroParser.Feature} for list of features)
     */
    public AvroFactory enable(AvroParser.Feature f) {
        _avroParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link AvroParser.Feature} for list of features)
     */
    public AvroFactory disable(AvroParser.Feature f) {
        _avroParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(AvroParser.Feature f) {
        return (_avroParserFeatures & f.getMask()) != 0;
    }

    @Override
    public int getFormatParserFeatures() {
        return _avroParserFeatures;
    }

    /*
    /**********************************************************
    /* Configuration, generator settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link AvroGenerator.Feature} for list of features)
     */
    public final AvroFactory configure(AvroGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link AvroGenerator.Feature} for list of features)
     */
    public AvroFactory enable(AvroGenerator.Feature f) {
        _avroGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link AvroGenerator.Feature} for list of features)
     */
    public AvroFactory disable(AvroGenerator.Feature f) {
        _avroGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(AvroGenerator.Feature f) {
        return (_avroGeneratorFeatures & f.getMask()) != 0;
    }

    @Override
    public int getFormatGeneratorFeatures() {
        return _avroGeneratorFeatures;
    }

    /*
    /**********************************************************
    /* Overridden parser factory methods
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public AvroParser createParser(File f) throws IOException {
        final IOContext ctxt = _createContext(_createContentReference(f), true);
        return _createParser(_decorate(new FileInputStream(f), ctxt), ctxt);
    }

    @Override
    public AvroParser createParser(URL url) throws IOException {
        final IOContext ctxt = _createContext(_createContentReference(url), true);
        return _createParser(_decorate(_optimizedStreamFromURL(url), ctxt), ctxt);
    }

    @Override
    public AvroParser createParser(InputStream in) throws IOException {
        final IOContext ctxt = _createContext(_createContentReference(in), false);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    //public JsonParser createParser(Reader r)

    @Override
    public AvroParser createParser(byte[] data) throws IOException {
        return createParser(data, 0, data.length);
    }

    @SuppressWarnings("resource")
    @Override
    public AvroParser createParser(byte[] data, int offset, int len) throws IOException {
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

    /**
     *<p>
     * note: co-variant return type
     */
    @Override
    public AvroGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        return createGenerator(out);
    }

    /**
     * Since Avro format always uses UTF-8 internally, no encoding need
     * to be passed to this method.
     */
    @Override
    public AvroGenerator createGenerator(OutputStream out) throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(_createContentReference(out), false);
        return _createGenerator(_decorate(out, ctxt), ctxt);
    }

    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    //protected IOContext _createContext(Object srcRef, boolean resourceManaged)

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected AvroParser _createParser(InputStream in, IOContext ctxt) throws IOException {
// !!! 21-Apr-2017, tatu: make configurable
        return new JacksonAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
//        return new ApacheAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
                _objectCodec, in);
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
    protected AvroParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException {
// !!! 21-Apr-2017, tatu: make configurable
        return new JacksonAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
//        return new ApacheAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
                _objectCodec, data, offset, len);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * generator.
     */
    @Override
    protected JsonGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        return _nonByteTarget();
    }

    //public BufferRecycler _getBufferRecycler()

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException {
        return _nonByteTarget();
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected AvroGenerator _createGenerator(OutputStream out, IOContext ctxt) throws IOException
    {
        int feats = _avroGeneratorFeatures;
        AvroGenerator gen = new AvroGenerator(ctxt, _generatorFeatures, feats,
                _objectCodec, out);
        return gen;
    }

    protected <T> T _nonByteSource() throws IOException {
        throw new UnsupportedOperationException("Can not create parser for character-based (not byte-based) source");
    }

    protected <T> T _nonByteTarget() throws IOException {
        throw new UnsupportedOperationException("Can not create generator for character-based (not byte-based) target");
    }
}