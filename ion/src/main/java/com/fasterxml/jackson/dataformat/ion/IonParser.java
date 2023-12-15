/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.amazon.ion.*;
import com.amazon.ion.system.IonSystemBuilder;

/**
 * Implementation of {@link JsonParser} that will use an underlying
 * {@link IonReader} as actual parser, and camouflage it as json parser.
 * Will not expose all Ion info (specifically, annotations)
 */
public class IonParser
    extends ParserMinimalBase
{
    /**
     * Enumeration that defines all togglable features for Ion parsers.
     */
    public enum Feature implements FormatFeature // in 2.12
    {
        /**
         * Whether to expect Ion native Type Id construct for indicating type (true);
         * or "generic" type property (false) when deserializing.
         *<p>
         * Enabled by default for backwards compatibility as that has been the behavior
         * of `jackson-dataformat-ion` since 2.9 (first official public version)
         *
         * @see <a href="https://amzn.github.io/ion-docs/docs/spec.html#annot">The Ion Specification</a>
         *
         * @since 2.12.3
         */
        USE_NATIVE_TYPE_ID(true),
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
        public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
        @Override
        public int getMask() { return _mask; }
    }

    // @since 2.14
    protected final static JacksonFeatureSet<StreamReadCapability> ION_READ_CAPABILITIES
        = DEFAULT_READ_CAPABILITIES.with(StreamReadCapability.EXACT_FLOATS);

    /*
    /*****************************************************************
    /* Basic configuration
    /*****************************************************************
     */

    protected final IonReader _reader;

    /**
     * Some information about source is passed here, including underlying
     * stream
     */
    protected final IOContext _ioContext;

    protected ObjectCodec _objectCodec;

    private final IonSystem _system;

    /*
    /*****************************************************************
    /* State
    /*****************************************************************
     */

    /**
     * Whether this logical parser has been closed or not
     */
    protected boolean _closed;

    /**
     * Information about context in structure hierarchy
     */
    protected JsonReadContext _parsingContext;

    /**
     * Type of value token we have; used to temporarily hold information
     * when pointing to field name
     */
    protected JsonToken _valueToken;

    /**
     * Bit flag composed of bits that indicate which
     * {@link IonParser.Feature}s
     * are enabled.
     */
    protected int _formatFeatures;

    /*
    /*****************************************************************
    /* Construction
    /*****************************************************************
     */

    /**
     * @deprecated use {@link IonFactory#createParser(IonReader) instead}
     */
    @Deprecated
    public IonParser(IonReader r, IOContext ctxt)
    {
        this(r, ctxt, null);
    }

    /**
     * @deprecated use {@link IonFactory#createParser(IonReader) instead}
     */
    @Deprecated
    public IonParser(IonReader r, IOContext ctxt, ObjectCodec codec) {
        this(r, IonSystemBuilder.standard().build(), ctxt, codec, IonFactory.DEFAULT_ION_PARSER_FEATURE_FLAGS);
    }

    /**
     * @since 2.13
     */
    IonParser(IonReader r, IonSystem system, IOContext ctxt, ObjectCodec codec, int ionParserFeatures) {
        this._reader = r;
        this._ioContext = ctxt;
        this._objectCodec = codec;
        this._parsingContext = JsonReadContext.createRootContext(-1, -1, null);
        this._system = system;
        this._formatFeatures = ionParserFeatures;
    }

    @Override
    public StreamReadConstraints streamReadConstraints() {
        return _ioContext.streamReadConstraints();
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /**
     * Accessor for {@link IonSystem} this parser instance was construted
     * with
     *
     * @return {@link IonSystem} instance
     *
     * @since 2.12.2
     */
    public IonSystem getIonSystem() {
        return _system;
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */

    @Override
    public boolean canReadTypeId() {
        // yes, Ion got 'em
        // 31-Mar-2021, manaigrn: but we might want to ignore them as per [dataformats-binary#270]
        return Feature.USE_NATIVE_TYPE_ID.enabledIn(_formatFeatures);
    }

    @Override
    public boolean requiresCustomCodec() { return false;}

    @Override
    public boolean hasTextCharacters() {
        //This is always false because getText() is more efficient than getTextCharacters().
        // See the javadoc for JsonParser.hasTextCharacters().
        return false;
    }

    @Override // since 2.12
    public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
        return ION_READ_CAPABILITIES;
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: state handling
    /*****************************************************************
     */

    @Override
    public boolean isClosed() {
        return _closed;
    }

    @Override
    public void close() throws IOException {
        if (!_closed) {
            // should only close if manage the resource
            if (_ioContext.isResourceManaged()) {
                Object src = _ioContext.contentReference().getRawContent();
                if (src instanceof Closeable) {
                    ((Closeable) src).close();
                }
            }
            _ioContext.close();
            _closed = true;
        }
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: Text value access
    /*****************************************************************
     */

    @Override
    public String getText() throws IOException
    {
         if (_currToken != null) { // null only before/after document
            switch (_currToken) {
            case FIELD_NAME:
                return currentName();
            case VALUE_STRING:
                try {
                    return _reader.stringValue();
                } catch (UnknownSymbolException e) {
                    // stringValue() will throw an UnknownSymbolException if we're
                    // trying to get the text for a symbol id that cannot be resolved.
                    // stringValue() has an assert statement which could throw an
                    throw _constructError(e.getMessage(), e);
                } catch (AssertionError e) {
                    // AssertionError if we're trying to get the text with a symbol
                    // id less than or equals to 0.
                    String msg = e.getMessage();
                    if (msg == null) {
                        msg = "UNKNOWN ROOT CAUSE";
                    }
                    throw _constructError("Internal `IonReader` error: "+msg, e);
                }
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                Number n = getNumberValue();
                return (n == null) ? null : n.toString();
            // Some special cases here:
            case VALUE_EMBEDDED_OBJECT:
                if (_reader.getType() == IonType.TIMESTAMP) {
                    Timestamp ts = _reader.timestampValue();
                    if (ts != null) return ts.toString();
                }
                // How about CLOB?
                break;
            default:
            }
            return _currToken.asString();
        }
        return null;
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        String str = getText();
        return (str == null) ? null : str.toCharArray();
    }

    @Override
    public int getTextLength() throws IOException {
        return getText().length();
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: Numeric value access
    /*****************************************************************
     */

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        return _reader.bigIntegerValue();
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        return _reader.bigDecimalValue();
    }

    @Override
    public double getDoubleValue() throws IOException {
        return _reader.doubleValue();
    }

    @Override
    public float getFloatValue() throws IOException {
        return (float) _reader.doubleValue();
    }

    @Override
    public int getIntValue() throws IOException {
        return _reader.intValue();
    }

    @Override
    public long getLongValue() throws IOException {
        return _reader.longValue();
    }

    @Override
    public NumberType getNumberType() throws IOException
    {
        IonType type = _reader.getType();
        if (type != null) {
            // Hmmh. Looks like Ion gives little bit looser definition here;
            // harder to pin down exact type. But let's try some checks still.
            switch (type) {
            case DECIMAL:
                //Ion decimals can be arbitrary precision, need to read as big decimal
                return NumberType.BIG_DECIMAL;
            case INT:
                IntegerSize size = _reader.getIntegerSize();
                switch (size) {
                case INT:
                    return NumberType.INT;
                case LONG:
                    return NumberType.LONG;
                default:
                    return NumberType.BIG_INTEGER;
                }
            case FLOAT:
                return NumberType.DOUBLE;
            default:
            }
        }
        return null;
    }

    @Override
    public Number getNumberValue() throws IOException {
        NumberType nt = getNumberType();
        if (nt != null) {
            switch (nt) {
            case INT:
                return _reader.intValue();
            case LONG:
                return _reader.longValue();
            case FLOAT:
                return (float) _reader.doubleValue();
            case DOUBLE:
                return _reader.doubleValue();
            case BIG_DECIMAL:
                return _reader.bigDecimalValue();
            case BIG_INTEGER:
                return getBigIntegerValue();
            }
        }
        return null;
    }

    @Override // @since 2.12 -- for (most?) binary formats exactness guaranteed anyway
    public final Number getNumberValueExact() throws IOException {
        return getNumberValue();
    }

    /*
    /****************************************************************
    /* JsonParser implementation: Access to other (non-text/number) values
    /*****************************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant arg0) throws IOException
    {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            switch (_reader.getType()) {
            case BLOB:
            case CLOB: // looks like CLOBs are much like BLOBs...
                return _reader.newBytes();
            default:
            }
        }
        // 19-Jan-2010, tatus: how about base64 encoded Strings? Should we allow
        //    automatic decoding here?
        return null;
    }

    @SuppressWarnings("resource")
    private IonValue getIonValue() throws IOException {
        if (_system == null) {
            throw new IllegalStateException("This "+getClass().getSimpleName()+" instance cannot be used for IonValue mapping");
        }
        _currToken = JsonToken.VALUE_EMBEDDED_OBJECT;
        IonList l = _system.newEmptyList();
        IonWriter writer = _system.newWriter(l);
        writer.writeValue(_reader);
        IonValue v = l.get(0);
        v.removeFromContainer();
        return v;
    }

    @Override
    public Object getEmbeddedObject() throws IOException {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            switch (_reader.getType()) {
            case TIMESTAMP:
                try {
                    return _reader.timestampValue();
                } catch (IllegalArgumentException e) {
                    throw _constructError(String.format(
                            "Invalid embedded TIMESTAMP value, problem: %s", e.getMessage()),
                            e);
                }
            case BLOB:
            case CLOB:
                return _reader.newBytes();
            // What about CLOB?
            default:
            }
        }
        return getIonValue();
    }

    /*
    /**********************************************************
    /* Public API, Native Ids (type, object)
    /**********************************************************
     */

    /* getTypeId() wants to return a single type, but there may be multiple type annotations on an Ion value.
     * @see MultipleTypeIdResolver...
     * MultipleClassNameIdResolver#selectId
     */
    @Override
    public Object getTypeId() throws IOException {
        String[] typeAnnotations = getTypeAnnotations();
        // getTypeAnnotations signals "none" with an empty array, but getTypeId is allowed to return null
        return typeAnnotations.length == 0 ? null : typeAnnotations[0];
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: traversal
    /*****************************************************************
     */

    @Override
    public JsonLocation currentLocation() {
        return JsonLocation.NA;
    }

    @Override
    public JsonLocation currentTokenLocation() {
        return JsonLocation.NA;
    }

    @Deprecated // since 2.17
    @Override
    public JsonLocation getCurrentLocation() { return currentLocation(); }

    @Deprecated // since 2.17
    @Override
    public JsonLocation getTokenLocation() { return currentTokenLocation(); }

    @Override
    public String currentName() throws IOException {
        return _parsingContext.getCurrentName();
    }

    @Deprecated // since 2.17
    @Override
    public String getCurrentName() throws IOException { return currentName(); }
    
    @Override
    public JsonStreamContext getParsingContext() {
        return _parsingContext;
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        // special case: if we return field name, we know value type, return it:
        if (_currToken == JsonToken.FIELD_NAME) {
            return (_currToken = _valueToken);
        }
        // also, when starting array/object, need to create new context
        if (_currToken == JsonToken.START_OBJECT) {
            _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
            _reader.stepIn();
        } else if (_currToken == JsonToken.START_ARRAY) {
            _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
            _reader.stepIn();
        }

        // any more tokens in this scope?
        IonType type = null;
        try {
            type = _reader.next();
        } catch (IonException e) {
            _wrapError(e.getMessage(), e);
        } catch (IndexOutOfBoundsException e) {
            _constructError(e.getMessage(), e);
        }
        if (type == null) {
            if (_parsingContext.inRoot()) { // EOF?
                close();
                _currToken = null;
            } else {
                _parsingContext = _parsingContext.getParent();
                _currToken = _reader.isInStruct() ? JsonToken.END_OBJECT : JsonToken.END_ARRAY;
                _reader.stepOut();
            }
            return _currToken;
        }
        // Structs have field names; need to keep track:
        boolean inStruct = !_parsingContext.inRoot() && _reader.isInStruct();
        // (isInStruct can return true for the first value read if the reader
        // was created from an IonValue that has a parent container)
        try {
            // getFieldName() can throw an UnknownSymbolException if the text of the
            // field name symbol cannot be resolved.
            _parsingContext.setCurrentName(inStruct ? _reader.getFieldName() : null);
        } catch (UnknownSymbolException e) {
            _wrapError(e.getMessage(), e);
        }
        JsonToken t = _tokenFromType(type);
        // and return either field name first
        if (inStruct) {
            _valueToken = t;
            return (_currToken = JsonToken.FIELD_NAME);
        }
        // or just the value (for lists, root value)
        return (_currToken = t);
    }

    /**
     * @see com.fasterxml.jackson.dataformat.ion.polymorphism.IonAnnotationTypeDeserializer
     */
    public String[] getTypeAnnotations() throws JsonParseException {
        try {
            // Per its spec, will not return null
            return _reader.getTypeAnnotations();
        } catch (UnknownSymbolException e) {
            // IonReader.getTypeAnnotations() can throw an UnknownSymbolException if the text of any
            // the annotation symbols cannot be resolved.
            throw _constructError(e.getMessage(), e);
        }
    }

    @Override
    public JsonParser skipChildren() throws IOException
    {
       if (_currToken != JsonToken.START_OBJECT
            && _currToken != JsonToken.START_ARRAY) {
            return this;
        }
        int open = 1;

        /* Since proper matching of start/end markers is handled
         * by nextToken(), we'll just count nesting levels here
         */
        while (true) {
            JsonToken t = nextToken();
            if (t == null) {
                _handleEOF(); // won't return in this case...
                return this;
            }
            switch (t) {
            case START_OBJECT:
            case START_ARRAY:
                ++open;
                break;
            case END_OBJECT:
            case END_ARRAY:
                if (--open == 0) {
                    return this;
                }
                break;
            default:
            }
        }
    }

    /*
     *****************************************************************
     * Internal helper methods
     *****************************************************************
     */

    protected JsonToken _tokenFromType(IonType type)
    {
        // One twist: Ion exposes nulls as typed ones... so:
        if (_reader.isNullValue()) {
            return JsonToken.VALUE_NULL;
        }

        switch (type) {
        case BOOL:
            return _reader.booleanValue() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        case DECIMAL:
        case FLOAT:
            return JsonToken.VALUE_NUMBER_FLOAT;
        case INT:
            return JsonToken.VALUE_NUMBER_INT;
        case STRING:
        case SYMBOL:
            return JsonToken.VALUE_STRING;
            /* This is bit trickier: Ion has precise understanding of what it is... but
             * to retain that knowledge, let's consider it an embedded object for now
             */
        case NULL: // I guess this must then be "untyped" null?
            return JsonToken.VALUE_NULL;
        case LIST:
        case SEXP:
            return JsonToken.START_ARRAY;
        case STRUCT:
            return JsonToken.START_OBJECT;
        case TIMESTAMP:
            return JsonToken.VALUE_EMBEDDED_OBJECT;
        default:
        }
        // and actually everything also as sort of alien thing...
        // (BLOB, CLOB)
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    /**
     * Method called when an EOF is encountered between tokens.
     */
    @Override
    protected void _handleEOF() throws JsonParseException
    {
        if (!_parsingContext.inRoot()) {
            _reportError(": expected close marker for "+_parsingContext.typeDesc()+" (from "
                    +_parsingContext.startLocation(_ioContext.contentReference())+")");
        }
    }

    @Override
    public void overrideCurrentName(String name) {
        try {
            _parsingContext.setCurrentName(name);
        } catch (Exception e) {
            // JsonReadContext.setCurrentName started throwing
            // JsonProcessingException in Jackson 2.3; allow compiling against
            // both versions.
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }
}
