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

package tools.jackson.dataformat.ion;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import tools.jackson.core.*;
import tools.jackson.core.base.ParserMinimalBase;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.util.SimpleStreamReadContext;
import tools.jackson.core.util.JacksonFeatureSet;

import com.amazon.ion.*;

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
    public enum Feature implements FormatFeature
    {
        /**
         * Whether to expect Ion native Type Id construct for indicating type (true);
         * or "generic" type property (false) when deserializing.
         *<p>
         * Enabled by default for backwards compatibility as that has been the behavior
         * of `jackson-dataformat-ion` since 2.9 (first official public version)
         *
         * @see <a href="https://amzn.github.io/ion-docs/docs/spec.html#annot">The Ion Specification</a>
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
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */

    protected final IonReader _reader;

    private final IonSystem _system;

    /**
     * Bit flag composed of bits that indicate which
     * {@link IonParser.Feature}s are enabled.
     */
    protected int _formatFeatures;

    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    /**
     * Information about context in structure hierarchy
     */
    protected SimpleStreamReadContext _streamReadContext;

    /**
     * Type of value token we have; used to temporarily hold information
     * when pointing to field name
     */
    protected JsonToken _valueToken;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    public IonParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            int streamReadFeatures, int formatFeatures,
            IonReader r, IonSystem system)
    {
        super(readCtxt, ioCtxt, streamReadFeatures);
        _reader = r;
        _formatFeatures = formatFeatures;
        // No DupDetector in use (yet?)
        _streamReadContext = SimpleStreamReadContext.createRootContext(-1, -1, null);
        _system = system;
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

    /**
     * Accessor needed for testing
     */
    public IOContext ioContext() {
        return _ioContext;
    }

    /*
    /**********************************************************************
    /* Capability, config introspection
    /**********************************************************************
     */

    @Override
    public boolean canReadTypeId() {
        // yes, Ion got 'em
        // 31-Mar-2021, manaigrn: but we might want to ignore them as per [dataformats-binary#270]
        return Feature.USE_NATIVE_TYPE_ID.enabledIn(_formatFeatures);
    }

    @Override
    public boolean hasTextCharacters() {
        //This is always false because getText() is more efficient than getTextCharacters().
        // See the javadoc for JsonParser.hasTextCharacters().
        return false;
    }

    @Override
    public JacksonFeatureSet<StreamReadCapability> streamReadCapabilities() {
        return ION_READ_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* JsonParser implementation: input state handling
    /**********************************************************************
     */

    // Default close() is fine:
    // public void close() { }

    @Override
    protected void _closeInput() throws IOException {
        // should only close if manage the resource
        if (_ioContext.isResourceManaged()) {
            Object src = _ioContext.contentReference().getRawContent();
            if (src instanceof Closeable) {
                try {
                    ((Closeable) src).close();
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
        }
    }

    @Override
    protected void _releaseBuffers() { }

    @Override
    public IonReader streamReadInputSource() {
        return _reader;
    }

    /*
    /**********************************************************************
    /* JsonParser implementation: Text value access
    /**********************************************************************
     */

    @Override
    public String getText() throws JacksonException
    {
         if (_currToken != null) { // null only before/after document
            switch (_currToken) {
            case PROPERTY_NAME:
                return currentName();
            case VALUE_STRING:
                try {
                    return _reader.stringValue();
                } catch (UnknownSymbolException
                    // stringValue() will throw an UnknownSymbolException if we're
                    // trying to get the text for a symbol id that cannot be resolved.
                    // stringValue() has an assert statement which could throw an
                    | AssertionError | NullPointerException e
                    // AssertionError if we're trying to get the text with a symbol
                    // id less than or equals to 0.
                    // NullPointerException may also be thrown on invalid data
                    ) {
                    return _reportCorruptContent(e);
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
    public char[] getTextCharacters() throws JacksonException {
        String str = getText();
        return (str == null) ? null : str.toCharArray();
    }

    @Override
    public int getTextLength() throws JacksonException {
        String str = getText();
        return (str == null) ? 0 : str.length();
    }

    @Override
    public int getTextOffset() throws JacksonException {
        return 0;
    }

    /*
    /**********************************************************************
    /* JsonParser implementation: Numeric value access
    /**********************************************************************
     */

    @Override
    public BigInteger getBigIntegerValue() throws JacksonException {
        _verifyIsNumberToken();
        return _getBigIntegerValue();
    }

    // @since 2.17
    private BigInteger _getBigIntegerValue() throws JacksonException {
        try {
            return _reader.bigIntegerValue();
        } catch (IonException
                // 01-Jan-2024, tatu: OSS-Fuzz#65062 points to AIOOBE:
                | ArrayIndexOutOfBoundsException e) {
            return _reportCorruptNumber(e);
        }
    }

    @Override
    public BigDecimal getDecimalValue() throws JacksonException {
        _verifyIsNumberToken();
        return _getBigDecimalValue();
    }

    // @since 2.17
    private BigDecimal _getBigDecimalValue() throws JacksonException {
        try {
            return _reader.bigDecimalValue();
        } catch (IonException
                // 01-Jan-2024, tatu: OSS-Fuzz#65062 points to AIOOBE:
                | ArrayIndexOutOfBoundsException
                // 05-Jan-2024, tatu: OSS-Fuzz#65557 points to NPE:
                | NullPointerException e) {
            return _reportCorruptNumber(e);
        }
    }

    @Override
    public double getDoubleValue() throws JacksonException {
        _verifyIsNumberToken();
        return _reader.doubleValue();
    }

    @Override
    public float getFloatValue() throws JacksonException {
        _verifyIsNumberToken();
        return (float) _reader.doubleValue();
    }

    @Override
    public int getIntValue() throws JacksonException {
        _verifyIsNumberToken();
        return _reader.intValue();
    }

    @Override
    public long getLongValue() throws JacksonException {
        _verifyIsNumberToken();
        return _reader.longValue();
    }

    // @since 2.17
    private void _verifyIsNumberToken() throws JacksonException
    {
        if (_currToken != JsonToken.VALUE_NUMBER_INT && _currToken != JsonToken.VALUE_NUMBER_FLOAT) {
            // Same as `ParserBase._parseNumericValue()` exception:
            throw _constructReadException("Current token (%s) not numeric, can not use numeric value accessors",
                    _currToken);
        }
    }

    @Override
    public NumberType getNumberType() throws JacksonException
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
                    // [dataformats-binary#434]: another problem with corrupt data handling.
                    // Temporary measure until this bug fixing is merged and published
                    // https://github.com/amazon-ion/ion-java/issues/685
                    try {
                        size = _reader.getIntegerSize();
                    } catch (IonException e) {
                        return _reportCorruptNumber(e);
                    } catch (NullPointerException e) {
                        return _reportCorruptContent(e);
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
                return NumberTypeFP.FLOAT32;
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
    public Number getNumberValue() throws JacksonException {
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
                return _getBigDecimalValue();
            case BIG_INTEGER:
                return _getBigIntegerValue();
            }
        }
        return null;
    }

    @Override
    public final Number getNumberValueExact() throws JacksonException {
        return getNumberValue();
    }

    // @TODO -- 27-Jun-2020, tatu: 3.0 requires parser implementations to define
    //  and I _think_ this should be implemented, assuming Ion allows some Not-a-Number
    //  values for floating-point types?
    @Override
    public boolean isNaN() throws JacksonException {
        return false;
    }

    /*
    /**********************************************************************
    /* JsonParser implementation: Access to other (non-text/number) values
    /**********************************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant arg0) throws JacksonException
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
    private IonValue getIonValue() throws JacksonException {
        if (_system == null) {
            throw new IllegalStateException("This "+getClass().getSimpleName()+" instance cannot be used for IonValue mapping");
        }
        _currToken = JsonToken.VALUE_EMBEDDED_OBJECT;
        IonList l = _system.newEmptyList();
        IonWriter writer = _system.newWriter(l);
        try {
            writer.writeValue(_reader);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        IonValue v = l.get(0);
        v.removeFromContainer();
        return v;
    }

    @Override
    public Object getEmbeddedObject() throws JacksonException {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            switch (_reader.getType()) {
            case TIMESTAMP:
                try {
                    return _reader.timestampValue();
                } catch (IllegalArgumentException e) {
                    throw _constructReadException(String.format(
                            "Invalid embedded TIMESTAMP value, problem: %s", e.getMessage()),
                            e);
                }
            case BLOB:
            case CLOB:
                try {
                    return _reader.newBytes();
                } catch (NullPointerException e) {
                    // 02-Jan-2024, tatu: OSS-Fuzz#65479 points to NPE ^^^
                    return _reportCorruptContent(e);
                }
            // What about CLOB?
            default:
            }
        }
        return getIonValue();
    }

    /*
    /**********************************************************************
    /* Public API, Native Ids (type, object)
    /**********************************************************************
     */

    /* getTypeId() wants to return a single type, but there may be multiple type annotations on an Ion value.
     * @see MultipleTypeIdResolver...
     * MultipleClassNameIdResolver#selectId
     */
    @Override
    public Object getTypeId() throws JacksonException {
        String[] typeAnnotations = getTypeAnnotations();
        // getTypeAnnotations signals "none" with an empty array, but getTypeId is allowed to return null
        return typeAnnotations.length == 0 ? null : typeAnnotations[0];
    }

    /*
    /**********************************************************************
    /* JsonParser implementation: traversal
    /**********************************************************************
     */

    @Override
    public JsonLocation currentLocation() {
        return JsonLocation.NA;
    }

    @Override
    public String currentName() throws JacksonException {
        return _streamReadContext.currentName();
    }

    @Override public TokenStreamContext streamReadContext() {  return _streamReadContext; }
    @Override public void assignCurrentValue(Object v) { _streamReadContext.assignCurrentValue(v); }
    @Override public Object currentValue() { return _streamReadContext.currentValue(); }

    @Override
    public JsonLocation currentTokenLocation() {
        return JsonLocation.NA;
    }

    @Override
    public JsonToken nextToken() throws JacksonException
    {
        // special case: if we return field name, we know value type, return it:
        if (_currToken == JsonToken.PROPERTY_NAME) {
            return (_currToken = _valueToken);
        }
        // also, when starting array/object, need to create new context
        if (_currToken == JsonToken.START_OBJECT) {
            _streamReadContext = _streamReadContext.createChildObjectContext(-1, -1);
            _reader.stepIn();
        } else if (_currToken == JsonToken.START_ARRAY) {
            _streamReadContext = _streamReadContext.createChildArrayContext(-1, -1);
            _reader.stepIn();
        }

        // any more tokens in this scope?
        IonType type = null;
        try {
            type = _reader.next();
        } catch (IonException e) {
            return _reportCorruptContent(e);

        } catch (AssertionError | IndexOutOfBoundsException | NullPointerException e) {
            // [dataformats-binary#420]: IonJava leaks IOOBEs, catch
            // [dataformats-binary#432]: AssertionError if we're trying to get the text
            //   with a symbol id less than or equals to 0.
            return _reportCorruptContent(e);
        }
        if (type == null) {
            if (_streamReadContext.inRoot()) { // EOF?
                close();
                _currToken = null;
            } else {
                _streamReadContext = _streamReadContext.getParent();
                _currToken = _reader.isInStruct() ? JsonToken.END_OBJECT : JsonToken.END_ARRAY;
                _reader.stepOut();
            }
            return _currToken;
        }
        // Structs have field names; need to keep track:
        boolean inStruct = !_streamReadContext.inRoot() && _reader.isInStruct();
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
        _streamReadContext.setCurrentName(name);
        JsonToken t = _tokenFromType(type);
        // and return either field name first
        if (inStruct) {
            _valueToken = t;
            return (_currToken = JsonToken.PROPERTY_NAME);
        }
        // or just the value (for lists, root value)
        return (_currToken = t);
    }

    /**
     * @see tools.jackson.dataformat.ion.polymorphism.IonAnnotationTypeDeserializer
     */
    public String[] getTypeAnnotations() throws JacksonException {
        try {
            // Per its spec, will not return null
            return _reader.getTypeAnnotations();
        } catch (UnknownSymbolException e) {
            // IonReader.getTypeAnnotations() can throw an UnknownSymbolException if the text of any
            // the annotation symbols cannot be resolved.
            throw _constructReadException(e.getMessage(), e);
        }
    }

    @Override
    public JsonParser skipChildren() throws JacksonException
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
    /**********************************************************************
    /* Internal helper methods
    /**********************************************************************
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
    protected void _handleEOF() throws StreamReadException
    {
        if (!_streamReadContext.inRoot()) {
            _reportError(": expected close marker for "+_streamReadContext.typeDesc()+" (from "
                    +_streamReadContext.startLocation(_ioContext.contentReference())+")");
        }
    }

    private <T> T _reportCorruptContent(Throwable e) throws StreamReadException
    {
        String origMsg = e.getMessage();
        if (origMsg == null) {
            origMsg = "[no exception message]";
        }
        final String msg = String.format("Corrupt content to decode; underlying `IonReader` problem: (%s) %s",
                e.getClass().getName(), origMsg);
        throw _constructReadException(msg, e);
    }

    private <T> T _reportCorruptNumber(Throwable e) throws StreamReadException
    {
        String origMsg = e.getMessage();
        if (origMsg == null) {
            origMsg = "[no exception message]";
        }
        final String msg = String.format("Corrupt Number value to decode; underlying `IonReader` problem: (%s) %s",
                e.getClass().getName(), origMsg);
        throw _constructReadException(msg, e);
    }
}
