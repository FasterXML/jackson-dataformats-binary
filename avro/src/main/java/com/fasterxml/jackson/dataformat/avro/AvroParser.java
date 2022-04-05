package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.io.Writer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
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
        implements FormatFeature // since 2.7
    {
        /**
         * Feature that can be disabled to prevent Avro from buffering any more
         * data then absolutely necessary.
         * This affects buffering by underlying `SnakeYAML` codec.
         *<p>
         * Enabled by default to preserve the existing behavior.
         *
         * @since 2.7
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

    // @since 2.14 - require some overrides
    protected final static JacksonFeatureSet<StreamReadCapability> AVRO_READ_CAPABILITIES =
            DEFAULT_READ_CAPABILITIES.with(StreamReadCapability.EXACT_FLOATS);

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;

    protected AvroSchema _rootSchema;

    protected int _formatFeatures;

    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    protected AvroReadContext _avroContext;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected AvroParser(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec)
    {
        super(ctxt, parserFeatures);    
        _objectCodec = codec;
        _formatFeatures = avroFeatures;
        _avroContext = MissingReader.instance;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    @Override
    public abstract Object getInputSource();

    // ensure impl defines
    @Override
    public abstract JsonParser overrideFormatFeatures(int values, int mask);

    @Override // since 2.12
    public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
        return AVRO_READ_CAPABILITIES;
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
    /* ParserBase method impls
    /**********************************************************                              
     */

    @Override
    protected void _finishString() throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    protected abstract void _closeInput() throws IOException;

    /*
    /***************************************************
    /* Public API, configuration
    /***************************************************
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

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof AvroSchema);
    }

    @Override public AvroSchema getSchema() {
        return _rootSchema;
    }

    @Override
    public void setSchema(FormatSchema schema)
    {
        if (_rootSchema == schema) {
            return;
        }
        if (schema instanceof AvroSchema) {
            // 27-Jan-2016, tatu: Crap, can't expose exception directly.
            try {
                _initSchema((AvroSchema) schema);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            throw new IllegalArgumentException("Can not use FormatSchema of type "
                    +schema.getClass().getName());
        }
    }

    protected abstract void _initSchema(AvroSchema schema) throws JsonProcessingException;

    @Override
    public boolean canReadTypeId() {
        return true;
    }

    @Override
    public Object getTypeId() throws IOException {
        return _avroContext != null ? _avroContext.getTypeId() : null;
    }

    /*
    /**********************************************************
    /* Location info
    /**********************************************************
     */

    @Override
    public JsonLocation getTokenLocation()
    {
        // !!! TODO
        return null;
    }

    @Override
    public JsonLocation getCurrentLocation()
    {
        // !!! TODO
        return null;
    }

    /*
    /**********************************************************
    /* Parsing
    /**********************************************************
     */
    
    @Override
    public abstract JsonToken nextToken() throws IOException;

    /*
    /**********************************************************
    /* String value handling
    /**********************************************************
     */

    @Override
    public abstract boolean hasTextCharacters();

    @Override
    public abstract String getText() throws IOException;

    @Override
    public abstract int getText(Writer writer) throws IOException;
    
    @Override
    public String getCurrentName() throws IOException {
        return _avroContext.getCurrentName();
    }

    @Override
    public void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        JsonReadContext ctxt = _parsingContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        /* 24-Sep-2013, tatu: Unfortunate, but since we did not expose exceptions,
         *   need to wrap this here
         */
        try {
            ctxt.setCurrentName(name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
}
