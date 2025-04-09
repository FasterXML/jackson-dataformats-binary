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

import com.amazon.ion.*;
import com.amazon.ion.system.IonSystemBuilder;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;

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
        /**
         * Whether to convert "null" to an IonValueNull (true);
         * or leave as a java null (false) when deserializing.
         *<p>
         * Enabled by default for backwards compatibility as that has been the behavior
         * of `jackson-dataformat-ion` since 2.13.
         *
         * @see <a href="https://amzn.github.io/ion-docs/docs/spec.html#annot">The Ion Specification</a>
         *
         * @since 2.19.0
         */
        READ_NULL_AS_IONVALUE(true),
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
        super(ctxt.streamReadConstraints());
        this._reader = r;
        this._ioContext = ctxt;
        this._objectCodec = codec;
        this._parsingContext = JsonReadContext.createRootContext(-1, -1, null);
        this._system = system;
        this._formatFeatures = ionParserFeatures;
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
                } catch (IonException e) {
                    return _reportCorruptContent(e);
                }
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                Number n = getNumberValue();
                return (n == null) ? null : n.toString();
            // Some special cases here:
            case VALUE_EMBEDDED_OBJECT:
                if (_reader.getType() == IonType.TIMESTAMP) {
                    Timestamp ts = _timestampFromIonReader();
                    if (ts != null) {
                        return ts.toString();
                    }
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
        String str = getText();
        return (str == null) ? 0 : str.length();
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
        _verifyIsNumberToken();
        return _getBigIntegerValue();
    }

    // @since 2.17
    private BigInteger _getBigIntegerValue() throws IOException {
        try {
            return _reader.bigIntegerValue();
        } catch (IonException e) {
            return _reportCorruptNumber(e);
        }
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {

        _verifyIsNumberToken();
        return _getBigDecimalValue();
    }

    // @since 2.17
    private BigDecimal _getBigDecimalValue() throws IOException {
        try {
            return _reader.bigDecimalValue();
        } catch (IonException e) {
            return _reportCorruptNumber(e);
        }
    }

    @Override
    public double getDoubleValue() throws IOException {
        _verifyIsNumberToken();
        return _getDoubleValue();
    }

    // @since 2.17
    private double _getDoubleValue() throws IOException {
        try {
            return _reader.doubleValue();
        } catch (IonException e) {
            return _reportCorruptNumber(e);
        }
    }

    @Override
    public float getFloatValue() throws IOException {
        _verifyIsNumberToken();
        // 04-May-2024, tatu: May seem odd but Ion really does not
        //   expose 32-bit floats even if it MAY use them internally
        //   for encoding. So:
        return (float) _getDoubleValue();
    }

    @Override
    public int getIntValue() throws IOException {
        _verifyIsNumberToken();
        return _getIntValue();
    }

    // @since 2.17
    private int _getIntValue() throws IOException {
        try {
            NumberType numberType = getNumberType();
            if (numberType == NumberType.LONG) {
                int result = _reader.intValue();
                if ((long) result != _reader.longValue()) {
                    this.reportOverflowInt();
                }
                return result;
            }
            if (numberType == NumberType.BIG_INTEGER) {
                BigInteger bigInteger = _reader.bigIntegerValue();
                if (BI_MIN_INT.compareTo(bigInteger) > 0 || BI_MAX_INT.compareTo(bigInteger) < 0) {
                    this.reportOverflowInt();
                }
                return bigInteger.intValue();
            } else {
                return _reader.intValue();
            }
        } catch (IonException e) {
            return _reportCorruptNumber(e);
        }
    }

    @Override
    public long getLongValue() throws IOException {
        _verifyIsNumberToken();
        return _getLongValue();
    }

    // @since 2.17
    private long _getLongValue() throws IOException {
        try {
            if (this.getNumberType() == NumberType.BIG_INTEGER) {
                BigInteger bigInteger = _reader.bigIntegerValue();
                if (BI_MIN_LONG.compareTo(bigInteger) > 0 || BI_MAX_LONG.compareTo(bigInteger) < 0) {
                    reportOverflowLong();
                }
                return bigInteger.longValue();
            } else {
                return _reader.longValue();
            }
        } catch (IonException e) {
            return _reportCorruptNumber(e);
        }
    }
    
    // @since 2.17
    private void _verifyIsNumberToken() throws IOException
    {
        if (_currToken != JsonToken.VALUE_NUMBER_INT && _currToken != JsonToken.VALUE_NUMBER_FLOAT) {
            // Same as `ParserBase._parseNumericValue()` exception:
            _reportError("Current token (%s) not numeric, can not use numeric value accessors",
                    _currToken);
        }
    }

    @Override
    public NumberType getNumberType() throws IOException
    {
        if (_currToken == JsonToken.VALUE_NUMBER_INT
                || _currToken == JsonToken.VALUE_NUMBER_FLOAT
                // 30-Dec-2023, tatu: This is odd, but some current tests seem to
                //    expect this case to work when creating `IonParser` from `IonReader`,
                //    which does not seem to work without work-around like this:
                || ((_currToken == null) && !isClosed())) {
            IonType type = _reader.getType();
            if (type != null) {
                // Hmmh. Looks like Ion gives little bit looser definition here;
                // harder to pin down exact type. But let's try some checks still.
                switch (type) {
                case DECIMAL:
                    //Ion decimals can be arbitrary precision, need to read as big decimal
                    return NumberType.BIG_DECIMAL;
                case INT:
                    final IntegerSize size;
                    try {
                        size = _reader.getIntegerSize();
                    } catch (IonException e) {
                        return _reportCorruptNumber(e);
                    }
                    if (size == null) {
                        _reportError("Current token (%s) not integer", _currToken);
                    }
                    switch (size) {
                    case INT:
                        return NumberType.INT;
                    case LONG:
                        return NumberType.LONG;
                    default:
                        return NumberType.BIG_INTEGER;
                    }
                case FLOAT:
                    // 04-May-2024, tatu: Ion really does not expose 32-bit floats, so:
                    return NumberType.DOUBLE;
                default:
                }
            }
        }
        return null;
    }

    @Override // since 2.17
    public NumberTypeFP getNumberTypeFP() throws IOException
    {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            final IonType type = _reader.getType();
            if (type == IonType.FLOAT) {
                // 06-Jan-2024, tatu: Existing code maps Ion `FLOAT` into Java
                //    `float`. But code in `IonReader` suggests `Double` might
                //    be more accurate mapping... odd.
                // 04-May-2024, tatu: Ion really does not expose 32-bit floats;
                //    must expose as 64-bit here too
                return NumberTypeFP.DOUBLE64;
            }
            if (type == IonType.DECIMAL) {
                // 06-Jan-2024, tatu: Seems like `DECIMAL` is expected to map
                //    to `BigDecimal`, as per existing code so:
                return NumberTypeFP.BIG_DECIMAL;
            }
        }
        return NumberTypeFP.UNKNOWN;
    }

    @Override
    public Number getNumberValue() throws IOException {
        NumberType nt = getNumberType();
        if (nt != null) {
            switch (nt) {
            case INT:
                return _getIntValue();
            case LONG:
                return _getLongValue();
            case FLOAT:
                return (float) _getDoubleValue();
            case DOUBLE:
                return _getDoubleValue();
            case BIG_DECIMAL:
                return _getBigDecimalValue();
            case BIG_INTEGER:
                return _getBigIntegerValue();
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
                return _bytesFromIonReader();
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
        _updateToken(JsonToken.VALUE_EMBEDDED_OBJECT);
        IonList l = _system.newEmptyList();
        IonWriter writer = _system.newWriter(l);
        writer.writeValue(_reader);
        IonValue v = l.get(0);
        v.removeFromContainer();

        if (v.isNullValue() && !Feature.READ_NULL_AS_IONVALUE.enabledIn(_formatFeatures)) {
            if (_valueToken == JsonToken.VALUE_NULL && !IonType.isContainer(v.getType())) {
                return null;
            }
        }
        return v;
    }

    @Override
    public Object getEmbeddedObject() throws IOException {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            switch (_reader.getType()) {
            case TIMESTAMP:
                return _timestampFromIonReader();
            case BLOB:
            case CLOB:
                return _bytesFromIonReader();
            // What about CLOB?
            default:
            }
        }
        return getIonValue();
    }

    // @since 2.17
    private byte[] _bytesFromIonReader() throws IOException {
        return _reader.newBytes();
    }

    // @since 2.17
    private Timestamp _timestampFromIonReader() throws IOException {
        try {
            return _reader.timestampValue();
        } catch (IllegalArgumentException e) {
            throw _constructError(String.format(
                    "Invalid embedded TIMESTAMP value, problem: %s", e.getMessage()),
                    e);
        }
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
            return _updateToken(_valueToken);
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
            return _reportCorruptContent(e);

        }
        if (type == null) {
            if (_parsingContext.inRoot()) { // EOF?
                _updateTokenToNull();
            } else {
                _parsingContext = _parsingContext.getParent();
                _updateToken(_reader.isInStruct() ? JsonToken.END_OBJECT : JsonToken.END_ARRAY);
                _reader.stepOut();
            }
            return _currToken;
        }
        // Structs have field names; need to keep track:
        boolean inStruct = !_parsingContext.inRoot() && _reader.isInStruct();
        // (isInStruct can return true for the first value read if the reader
        // was created from an IonValue that has a parent container)
        final String name;
        try {
            // getFieldName() can throw an UnknownSymbolException if the text of the
            // field name symbol cannot be resolved.
            name = inStruct ? _reader.getFieldName() : null;
        } catch (IonException e) {
            return _reportCorruptContent(e);
        }
        _parsingContext.setCurrentName(name);
        JsonToken t = _tokenFromType(type);
        // and return either field name first
        if (inStruct) {
            _valueToken = t;
            return _updateToken(JsonToken.FIELD_NAME);
        }
        // or just the value (for lists, root value)
        return _updateToken(t);
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

        // Since proper matching of start/end markers is handled
        // by nextToken(), we'll just count nesting levels here
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

    private <T> T _reportCorruptContent(Throwable e) throws IOException
    {
        String origMsg = e.getMessage();
        if (origMsg == null) {
            origMsg = "[no exception message]";
        }
        final String msg = String.format("Corrupt content to decode; underlying `IonReader` problem: (%s) %s",
                e.getClass().getName(), origMsg);
        throw _constructError(msg, e);
    }

    private <T> T _reportCorruptNumber(Throwable e) throws IOException
    {
        String origMsg = e.getMessage();
        if (origMsg == null) {
            origMsg = "[no exception message]";
        }
        final String msg = String.format("Corrupt Number value to decode; underlying `IonReader` problem: (%s) %s",
                e.getClass().getName(), origMsg);
        throw _constructError(msg, e);
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
