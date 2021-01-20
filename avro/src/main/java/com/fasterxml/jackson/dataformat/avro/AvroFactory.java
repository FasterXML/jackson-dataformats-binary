package com.fasterxml.jackson.dataformat.avro;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.BinaryTSFactory;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroParserImpl;
import com.fasterxml.jackson.dataformat.avro.deser.*;

/**
 * Default {@link TokenStreamFactory} implementation for encoding/decoding Avro
 * content, uses native Jackson encoder/decoder.
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

    /**
     * Flag that is set if Apache Avro lib's decoder is to be used for decoding;
     * `false` to use Jackson native Avro decoder.
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
    public AvroFactory()
    {
        // 09-Jan-2017, tatu: We must actually create and pass builder to be able to change
        //    one of JsonGenerator.Featuers (See builder for details)
        super(new AvroFactoryBuilder());
    }

    protected AvroFactory(AvroFactory src)
    {
        super(src);
        _useApacheLibDecoder = src._useApacheLibDecoder;
    }

    /**
     * Constructors used by {@link AvroFactoryBuilder} for instantiation.
     *
     * @since 3.0
     */
    protected AvroFactory(AvroFactoryBuilder b)
    {
        super(b);
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
    public AvroFactory copy() {
        return new AvroFactory(this);
    }

    /**
     * Instances are immutable so just return `this`
     */
    @Override
    public TokenStreamFactory snapshot() {
        return this;
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
    protected Object readResolve() {
        return new AvroFactory(this);
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

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(AvroParser.Feature f) {
        return (_formatReadFeatures & f.getMask()) != 0;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(AvroGenerator.Feature f) {
        return (_formatWriteFeatures & f.getMask()) != 0;
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

    @Override
    public Class<AvroParser.Feature> getFormatReadFeatureType() {
        return AvroParser.Feature.class;
    }

    @Override
    public Class<AvroGenerator.Feature> getFormatWriteFeatureType() {
        return AvroGenerator.Feature.class;
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
    protected AvroParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in) throws JacksonException
    {
        if (_useApacheLibDecoder) {
          return new ApacheAvroParserImpl(readCtxt, ioCtxt,
                  readCtxt.getStreamReadFeatures(_streamReadFeatures),
                  readCtxt.getFormatReadFeatures(_formatReadFeatures),
                  (AvroSchema) readCtxt.getSchema(),
                  in);
        }
        return new JacksonAvroParserImpl(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                (AvroSchema) readCtxt.getSchema(),
                in);
    }

    @Override
    protected AvroParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len)
        throws JacksonException
    {
        if (_useApacheLibDecoder) {
            return new ApacheAvroParserImpl(readCtxt, ioCtxt,
                    readCtxt.getStreamReadFeatures(_streamReadFeatures),
                    readCtxt.getFormatReadFeatures(_formatReadFeatures),
                    (AvroSchema) readCtxt.getSchema(),
                    data, offset, len);
        }
        return new JacksonAvroParserImpl(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                (AvroSchema) readCtxt.getSchema(),
                data, offset, len);
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
    /******************************************************
    /* Factory method impls: generators
    /******************************************************
     */
    
    @Override
    protected JsonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IOContext ioCtxt, OutputStream out)
        throws JacksonException
    {
        return new AvroGenerator(writeCtxt, ioCtxt,
                writeCtxt.getStreamWriteFeatures(_streamWriteFeatures),
                writeCtxt.getFormatWriteFeatures(_formatWriteFeatures),
                out,
                (AvroSchema) writeCtxt.getSchema());
    }
}
