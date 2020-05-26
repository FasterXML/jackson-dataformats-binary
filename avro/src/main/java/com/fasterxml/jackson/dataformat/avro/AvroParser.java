package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.io.Writer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.core.util.SimpleTokenReadContext;
import com.fasterxml.jackson.core.util.VersionUtil;
import com.fasterxml.jackson.dataformat.avro.deser.AvroReadContext;
import com.fasterxml.jackson.dataformat.avro.deser.MissingReader;

/**
 * {@link JsonParser} implementation for decoding Avro content and
 * exposing at as a stream of {@link JsonToken}s, to be used
 * for data binding.
 */
public abstract class AvroParser extends ParserBase
{
    /**
     * Enumeration that defines all togglable features for Avro parsers.
     */
    public enum Feature
        implements FormatFeature
    {
        /**
         * Feature that can be disabled to prevent Avro from buffering any more
         * data then absolutely necessary.
         *<p>
         * Enabled by default to preserve the existing behavior.
         */
        AVRO_BUFFERING(true)
        ;

        final boolean _defaultState;
        final int _mask;

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        @Override
        public boolean enabledByDefault() { return _defaultState; }
        @Override
        public int getMask() { return _mask; }
        @Override
        public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
    }

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    protected AvroSchema _rootSchema;

    protected int _formatFeatures;

    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected SimpleTokenReadContext _parsingContext;

    protected AvroReadContext _avroContext;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected AvroParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, int avroFeatures)
    {
        super(readCtxt, ioCtxt, parserFeatures);    
        _formatFeatures = avroFeatures;
        // null -> No dup checks in Avro (would only be relevant for Maps)
        _parsingContext = SimpleTokenReadContext.createRootContext(null);
        _avroContext = MissingReader.instance;
    }

    @Override
    public abstract Object getInputSource();

    /*                                                                                       
    /**********************************************************************
    /* Versioned                                                                             
    /**********************************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Capability introspection
    /**********************************************************************
     */

    @Override
    public boolean canReadTypeId() {
        return true;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof AvroSchema);
    }

    @Override
    public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
        // Defaults are fine
        return DEFAULT_READ_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* ParserBase method impls
    /**********************************************************************
     */

    @Override public TokenStreamContext getParsingContext() { return _parsingContext; }

    @Override public void setCurrentValue(Object v) { _parsingContext.setCurrentValue(v); }
    @Override public Object getCurrentValue() { return _parsingContext.getCurrentValue(); }

    @Override
    protected abstract void _closeInput() throws IOException;

    /*
    /**********************************************************************
    /* Public API, configuration
    /**********************************************************************
     */

    /**
     * Method for enabling specified Avro feature
     * (check {@link Feature} for list of features)
     */
    public JsonParser enable(AvroParser.Feature f) {
        _formatFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified Avro feature
     * (check {@link Feature} for list of features)
     */
    public JsonParser disable(AvroParser.Feature f) {
        _formatFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Method for enabling or disabling specified Avro feature
     * (check {@link Feature} for list of features)
     */
    public JsonParser configure(AvroParser.Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for checking whether specified Avro {@link Feature}
     * is enabled.
     */
    public boolean isEnabled(AvroParser.Feature f) {
        return (_formatFeatures & f.getMask()) != 0;
    }

    @Override public AvroSchema getSchema() {
        return _rootSchema;
    }

    protected void setSchema(AvroSchema schema)
    {
        if (_rootSchema == schema) {
            return;
        }
        try {
            _initSchema((AvroSchema) schema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected abstract void _initSchema(AvroSchema schema) throws JsonProcessingException;

    @Override
    public Object getTypeId() throws IOException {
        return _avroContext != null ? _avroContext.getTypeId() : null;
    }

    /*
    /**********************************************************************
    /* Location info
    /**********************************************************************
     */

    @Override
    public JsonLocation getTokenLocation() {
        // !!! TODO
        return null;
    }

    @Override
    public JsonLocation getCurrentLocation() {
        // !!! TODO
        return null;
    }

    /*
    /**********************************************************************
    /* Parsing
    /**********************************************************************
     */
    
//    public abstract JsonToken nextToken() throws IOException;

    /*
    /**********************************************************************
    /* String value handling
    /**********************************************************************
     */

    @Override
    public abstract boolean hasTextCharacters();

    @Override
    public abstract String getText() throws IOException;

    @Override
    public abstract int getText(Writer writer) throws IOException;

    @Override
    public String currentName() throws IOException {
        return _avroContext.currentName();
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        String text = getText();
        return (text == null) ? null : text.toCharArray();
    }

    @Override
    public int getTextLength() throws IOException {
        String text = getText();
        return (text == null) ? 0 : text.length();
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    /*
    /**********************************************************************
    /* Binary (base64)
    /**********************************************************************
     */

    @Override
    public Object getEmbeddedObject() throws IOException {
        return _binaryValue;
    }
    
    @Override
    public byte[] getBinaryValue(Base64Variant variant) throws IOException
    {
        // Usually we get properly declared byte[], and _binaryValue non null.
        // But we also support base64-encoded String as fallback
        if (_binaryValue == null) {
            if (_currToken != JsonToken.VALUE_STRING) {
                _reportError("Current token ("+_currToken+") not VALUE_STRING, can not access as binary");
            }
            @SuppressWarnings("resource")
            ByteArrayBuilder builder = _getByteArrayBuilder();
            _decodeBase64(getText(), builder, variant);
            _binaryValue = builder.toByteArray();
        }
        return _binaryValue;
    }

    /*
    /**********************************************************************
    /* And methods we shouldn't really need...
    /**********************************************************************
     */

    // We should never end up here, as all numeric values are eagerly decoded...

    @Override
    protected void _parseNumericValue(int expType) throws IOException {
        VersionUtil.throwInternal();
    }

    @Override
    protected int _parseIntValue() throws IOException {
        VersionUtil.throwInternal();
        return 0;
    }

}
