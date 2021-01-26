package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.BinaryTSFactory;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.BinaryNameMatcher;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.sym.PropertyNameMatcher;
import com.fasterxml.jackson.core.util.Named;

/**
 * Factory used for constructing {@link CBORParser} and {@link CBORGenerator}
 * instances; both of which handle
 * <a href="https://www.rfc-editor.org/info/rfc7049">CBOR</a>
 * encoded data.
 *<p>
 * Note on using non-byte-based sources/targets (char based, like
 * {@link java.io.Reader} and {@link java.io.Writer}): these can not be
 * used for CBOR documents; attempt will throw exception.
 * 
 * @author Tatu Saloranta
 */
public class CBORFactory
    extends BinaryTSFactory
    implements java.io.Serializable
{
	private static final long serialVersionUID = 1;

    /*
    /**********************************************************************
    /* Constants
    /**********************************************************************
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
    /**********************************************************************
    /* Symbol table management
    /**********************************************************************
     */

    /**
     * Alternative to the basic symbol table, some stream-based
     * parsers use different name canonicalization method.
     */
    protected final transient ByteQuadsCanonicalizer _byteSymbolCanonicalizer = ByteQuadsCanonicalizer.createRoot();

    /*
    /**********************************************************************
    /* Factory construction, configuration
    /**********************************************************************
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
    public CBORFactory() {
        super(DEFAULT_CBOR_PARSER_FEATURE_FLAGS,
                DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS);
    }

    public CBORFactory(CBORFactory src)
    {
        super(src);
    }

    /**
     * Constructors used by {@link CBORFactoryBuilder} for instantiation.
     *
     * @since 3.0
     */
    protected CBORFactory(CBORFactoryBuilder b) {
        super(b);
    }

    @Override
    public CBORFactoryBuilder rebuild() {
        return new CBORFactoryBuilder(this);
    }

    /**
     * Main factory method to use for constructing {@link CBORFactory} instances with
     * different configuration.
     */
    public static CBORFactoryBuilder builder() {
        return new CBORFactoryBuilder();
    }

    @Override
    public CBORFactory copy() {
        return new CBORFactory(this);
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
     * Also: must be overridden by sub-classes as well.
     */
    protected Object readResolve() {
        return new CBORFactory(this);
    }

    /*                                                                                       
    /**********************************************************************
    /* Capability introspection                                                                     
    /**********************************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public boolean canParseAsync() {
        // 30-Sep-2017, tatu: No async implementation exists yet
        return false;
    }

    /*
    /**********************************************************************
    /* Data format support
    /**********************************************************************
     */

    @Override
    public String getFormatName() {
        return FORMAT_NAME;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return false; // no (mandatory) FormatSchema for cbor
    }

    @Override
    public Class<CBORParser.Feature> getFormatReadFeatureType() {
        return CBORParser.Feature.class;
    }

    @Override
    public Class<CBORGenerator.Feature> getFormatWriteFeatureType() {
        return CBORGenerator.Feature.class;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(CBORParser.Feature f) {
        return f.enabledIn(_formatReadFeatures);
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(CBORGenerator.Feature f) {
        return f.enabledIn(_formatWriteFeatures);
    }

    /*
    /**********************************************************************
    /* Parser factory methods
    /**********************************************************************
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
    protected CBORParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in)
    {
        return new CBORParserBootstrapper(ioCtxt, in).constructParser(readCtxt,
                _factoryFeatures,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                _byteSymbolCanonicalizer);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    @Override
    protected CBORParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len)
    {
        return new CBORParserBootstrapper(ioCtxt, data, offset, len).constructParser(readCtxt,
                _factoryFeatures,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                _byteSymbolCanonicalizer);
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ctxt,
            DataInput input)
    {
        // 30-Sep-2017, tatu: As of now not supported although should be quite possible
        //    to support
        return _unsupported();
    }

    /*
    /**********************************************************************
    /* Generator factory methods
    /**********************************************************************
     */
    
    @Override
    protected JsonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IOContext ioCtxt, OutputStream out)
    {
        CBORGenerator gen = new CBORGenerator(writeCtxt, ioCtxt,
                writeCtxt.getStreamWriteFeatures(_streamWriteFeatures),
                writeCtxt.getFormatWriteFeatures(_formatWriteFeatures),
                out);
        if (CBORGenerator.Feature.WRITE_TYPE_HEADER.enabledIn(_formatWriteFeatures)) {
            gen.writeTag(CBORConstants.TAG_ID_SELF_DESCRIBE);
        }
        return gen;
    }

    /*
    /**********************************************************************
    /* Other factory methods
    /**********************************************************************
     */

    @Override
    public PropertyNameMatcher constructNameMatcher(List<Named> matches, boolean alreadyInterned) {
        return BinaryNameMatcher.constructFrom(matches, alreadyInterned);
    }

    @Override
    public PropertyNameMatcher constructCINameMatcher(List<Named> matches, boolean alreadyInterned,
            Locale locale) {
        return BinaryNameMatcher.constructCaseInsensitive(locale, matches, alreadyInterned);
    }
}
