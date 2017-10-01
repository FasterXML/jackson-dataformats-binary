package com.fasterxml.jackson.dataformat.avro;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.BinaryTSFactory;
import com.fasterxml.jackson.core.io.IOContext;

import com.fasterxml.jackson.dataformat.avro.deser.*;

/**
 * Default {@link JsonFactory} implementation for encoding/decoding Avro
 * content, uses native Jackson encoder/decoder.
 *
 * @see com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory
 */
public class AvroFactory
    extends BinaryTSFactory
    implements java.io.Serializable
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
    public AvroFactory() { this(null); }

    public AvroFactory(ObjectCodec oc)
    {
        super(oc);
        _avroParserFeatures = DEFAULT_AVRO_PARSER_FEATURE_FLAGS;
        _avroGeneratorFeatures = DEFAULT_AVRO_GENERATOR_FEATURE_FLAGS;

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
    }

    @Override
    public AvroFactory copy()
    {
        return new AvroFactory(this, null);
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
    protected Object readResolve() {
        return new AvroFactory(this, _objectCodec);
    }

    /*                                                                                       
    /**********************************************************                              
    /* Basic introspection                                                                  
    /**********************************************************                              
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    // Yes, Avro is strictly positional based on schema
    @Override
    public boolean requiresPropertyOrdering() {
        return true;
    }

    @Override
    public boolean canParseAsync() {
        // 30-Sep-2017, tatu: No async implementation exists yet
        return false;
    }

    /*
    /**********************************************************
    /* Data format support
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

    /*
    /******************************************************
    /* Factory method impls: parsers
    /******************************************************
     */

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
    protected AvroParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException {
// !!! 21-Apr-2017, tatu: make configurable
        return new JacksonAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
//        return new ApacheAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
                _objectCodec, data, offset, len);
    }

    @Override
    protected JsonParser _createParser(DataInput input, IOContext ctxt)
            throws IOException {
        // 30-Sep-2017, tatu: As of now not supported although should be quite possible
        //    to support
        return _unsupported();
    }

    /*
    /******************************************************
    /* Factory method impls: generators
    /******************************************************
     */
    
    @Override
    protected JsonGenerator _createGenerator(OutputStream out,
            IOContext ctxt) throws IOException {
        int feats = _avroGeneratorFeatures;
        AvroGenerator gen = new AvroGenerator(ctxt, _generatorFeatures, feats,
                _objectCodec, out);
        return gen;
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    /*
    protected AvroGenerator _createGenerator(OutputStream out, IOContext ctxt) throws IOException
    {
        int feats = _avroGeneratorFeatures;
        AvroGenerator gen = new AvroGenerator(ctxt, _generatorFeatures, feats,
                _objectCodec, out);
        return gen;
    }
    */
}
