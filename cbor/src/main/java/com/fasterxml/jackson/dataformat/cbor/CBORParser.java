package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.sym.PropertyNameMatcher;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;

import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.*;

public class CBORParser extends ParserBase
{
    /**
     * Enumeration that defines all togglable features for CBOR generators.
     */
    public enum Feature implements FormatFeature
    {
//        BOGUS(false)
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
        
        @Override public boolean enabledByDefault() { return _defaultState; }
        @Override public int getMask() { return _mask; }
        @Override public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
    }

    private final static Charset UTF8 = StandardCharsets.UTF_8;

    private final static int[] UTF8_UNIT_CODES = CBORConstants.sUtf8UnitLengths;

    // Constants for handling of 16-bit "mini-floats"
    private final static double MATH_POW_2_10 = Math.pow(2, 10);
    private final static double MATH_POW_2_NEG14 = Math.pow(2, -14);

    // [dataformats-binary#186] Avoid OOME/DoS for bigger binary;
    //  read only up to 250k
    protected final static int LONGEST_NON_CHUNKED_BINARY = 250_000;

    // @since 2.14 - require some overrides
    protected final static JacksonFeatureSet<StreamReadCapability> CBOR_READ_CAPABILITIES =
            DEFAULT_READ_CAPABILITIES.with(StreamReadCapability.EXACT_FLOATS);

    /*
    /**********************************************************************
    /* Parsing state
    /**********************************************************************
     */

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected CBORReadContext _streamReadContext;

    /**
     * Helper variables used when dealing with chunked content.
     */
    private int _chunkLeft, _chunkEnd;

    /**
     * We will keep track of tag value for possible future use.
     */
    protected int _tagValue = -1;

    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete = false;

    /**
     * Type byte of the current token
     */
    protected int _typeByte;

    // Base class has all other types, but no distinction between double, float, so
    protected float _numberFloat;

    /*
    /**********************************************************************
    /* Input source config, state
    /**********************************************************************
     */

    /**
     * Input stream that can be used for reading more content, if one
     * in use. May be null, if input comes just as a full buffer,
     * or if the stream has been closed.
     */
    protected InputStream _inputStream;

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but in some cases pre-loaded buffer
     * is handed to the parser.
     */
    protected byte[] _inputBuffer;

    /**
     * Flag that indicates whether the input buffer is recyclable (and
     * needs to be returned to recycler once we are done) or not.
     *<p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;

    /*
    /**********************************************************************
    /* Symbol handling, decoding
    /**********************************************************************
     */

    /**
     * Symbol table that contains field names encountered so far
     */
    final protected ByteQuadsCanonicalizer _symbols;

    /**
     * Temporary buffer used for name parsing.
     */
    protected int[] _quadBuffer = NO_INTS;

    /**
     * Quads used for hash calculation
     */
    protected int _quad1, _quad2, _quad3;

    /**
     * Marker flag to indicate that standard symbol handling is used
     * (one with symbol table assisted canonicalization. May be disabled
     * in which case alternate stream-line, non-canonicalizing handling
     * is used: usually due to set of symbols
     * (Object property names) is unbounded and will not benefit from
     * canonicalization attempts.
     *
     * @since 2.13
     */
    protected final boolean _symbolsCanonical;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public CBORParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, int cborFeatures,
            ByteQuadsCanonicalizer sym,
            InputStream in, byte[] inputBuffer, int start, int end,
            boolean bufferRecyclable)
    {
        super(readCtxt, ioCtxt, parserFeatures);
        _symbols = sym;
        _symbolsCanonical = sym.isCanonicalizing();

        _inputStream = in;
        _inputBuffer = inputBuffer;
        _inputPtr = start;
        _inputEnd = end;
        _bufferRecyclable = bufferRecyclable;
        DupDetector dups = StreamReadFeature.STRICT_DUPLICATE_DETECTION.enabledIn(parserFeatures)
                ? DupDetector.rootDetector(this) : null;
        _streamReadContext = CBORReadContext.createRootContext(dups);

        _tokenInputRow = -1;
        _tokenInputCol = -1;
    }

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
    /* Configuration
    /**********************************************************************
     */

    @Override
    public JacksonFeatureSet<StreamReadCapability> streamReadCapabilities() {
        return CBOR_READ_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
     */

    /**
     * Method that can be used to access tag id associated with
     * the most recently decoded value (whether completely, for
     * scalar values, or partially, for Objects/Arrays), if any.
     * If no tag was associated with it, -1 is returned.
     */
    public int getCurrentTag() {
        return _tagValue;
    }

    /*
    /**********************************************************************
    /* Abstract impls
    /**********************************************************************
     */

    @Override public TokenStreamContext streamReadContext() { return _streamReadContext; }
    @Override public void assignCurrentValue(Object v) { _streamReadContext.assignCurrentValue(v); }
    @Override public Object currentValue() { return _streamReadContext.currentValue(); }

    @Override
    public int releaseBuffered(OutputStream out)
    {
        int count = _inputEnd - _inputPtr;
        if (count < 1) {
            return 0;
        }
        // let's just advance ptr to end
        int origPtr = _inputPtr;
        try {
            out.write(_inputBuffer, origPtr, count);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return count;
    }
    
    @Override
    public Object streamReadInputSource() {
        return _inputStream;
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation currentTokenLocation()
    {
        // token location is correctly managed...
        return new JsonLocation(_ioContext.contentReference(),
                _tokenInputTotal, // bytes
                -1, -1, (int) _tokenInputTotal); // char offset, line, column
    }   

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation currentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new JsonLocation(_ioContext.contentReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public String currentName()
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            return _streamReadContext.getParent().currentName();
        }
        return _streamReadContext.currentName();
    }

    @Override
    public void close() {
        if (!_closed) {
            _closed = true;
            _symbols.release();
            try {
                _closeInput();
            } finally {
                // as per [JACKSON-324], do in finally block
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
        }
    }

    /*
    /**********************************************************************
    /* Overridden methods
    /**********************************************************************
     */

    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            // yes; is or can be made available efficiently as char[]
            return _textBuffer.hasTextAsCharacters();
        }
        // other types, no benefit from accessing as char[]
        return false;
    }

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
    @Override
    protected void _releaseBuffers()
    {
        super._releaseBuffers();
        if (_bufferRecyclable) {
            byte[] buf = _inputBuffer;
            if (buf != null) {
                _inputBuffer = null;
                _ioContext.releaseReadIOBuffer(buf);
            }
        }
    }

    /*
    /**********************************************************************
    /* JsonParser impl
    /**********************************************************************
     */

    @Override
    public JsonToken nextToken() throws JacksonException
    {
        _numTypesValid = NR_UNKNOWN;
        // For longer tokens (text, binary), we'll only read when requested
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;

        // First: need to keep track of lengths of defined-length Arrays and
        // Objects (to materialize END_ARRAY/END_OBJECT as necessary);
        // as well as handle names for Object entries.
        if (_streamReadContext.inObject()) {
            if (_currToken != JsonToken.PROPERTY_NAME) {
                _tagValue = -1;
                // completed the whole Object?
                if (!_streamReadContext.expectMoreValues()) {
                    _streamReadContext = _streamReadContext.getParent();
                    return (_currToken = JsonToken.END_OBJECT);
                }
                return (_currToken = _decodePropertyName());
            }
        } else {
            if (!_streamReadContext.expectMoreValues()) {
                _tagValue = -1;
                _streamReadContext = _streamReadContext.getParent();
                return (_currToken = JsonToken.END_ARRAY);
            }
        }
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                return _eofAsNextToken();
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        int type = (ch >> 5);
        int lowBits = ch & 0x1F;

        // One special case: need to consider tag as prefix first:
        if (type == 6) {
            _tagValue = Integer.valueOf(_decodeTag(lowBits));
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    return _eofAsNextToken();
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            type = (ch >> 5);
            lowBits = ch & 0x1F;
        } else {
            _tagValue = -1;
        }
        switch (type) {
        case 0: // positive int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = lowBits;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = _decode8Bits();
                    break;
                case 1:
                    _numberInt = _decode16Bits();
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v >= 0) {
                            _numberInt = v;
                        } else {
                            long l = (long) v;
                            _numberLong = l & 0xFFFFFFFFL;
                            _numTypesValid = NR_LONG;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = l;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigPositive(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            return (_currToken = JsonToken.VALUE_NUMBER_INT);
        case 1: // negative int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = -lowBits - 1;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = -_decode8Bits() - 1;
                    break;
                case 1:
                    _numberInt = -_decode16Bits() - 1;
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v < 0) {
                            long unsignedBase = (long) v & 0xFFFFFFFFL;
                            _numberLong = -unsignedBase - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberInt = -v - 1;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = -l - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigNegative(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            return (_currToken = JsonToken.VALUE_NUMBER_INT);

        case 2: // byte[]
            _typeByte = ch;
            _tokenIncomplete = true;
            if (_tagValue >= 0) {
                return _handleTaggedBinary(_tagValue);
            }
            return (_currToken = JsonToken.VALUE_EMBEDDED_OBJECT);

        case 3: // String
            _typeByte = ch;
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_STRING);

        case 4: // Array
            {
                int len = _decodeExplicitLength(lowBits);
                if (_tagValue >= 0) {
                    return _handleTaggedArray(_tagValue, len);
                }
                _streamReadContext = _streamReadContext.createChildArrayContext(len);
            }
            return (_currToken = JsonToken.START_ARRAY);

        case 5: // Object
            _currToken = JsonToken.START_OBJECT;
            {
                int len = _decodeExplicitLength(lowBits);
                _streamReadContext = _streamReadContext.createChildObjectContext(len);
            }
            return _currToken;

        case 6: // another tag; not allowed
            _reportError("Multiple tags not allowed per value (first tag: "+_tagValue+")");

        case 7:
        default: // misc: tokens, floats
            switch (lowBits) {
            case 20:
                return (_currToken = JsonToken.VALUE_FALSE);
            case 21:
                return (_currToken = JsonToken.VALUE_TRUE);
            case 22:
                return (_currToken = JsonToken.VALUE_NULL);
            case 23:
                return (_currToken = _decodeUndefinedValue());
                
            case 25: // 16-bit float... 
                // As per [http://stackoverflow.com/questions/5678432/decompressing-half-precision-floats-in-javascript]
                {
                    _numberFloat = (float) _decodeHalfSizeFloat();
                    _numTypesValid = NR_FLOAT;
                }
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            case 26: // Float32
                {
                    _numberFloat = Float.intBitsToFloat(_decode32Bits());
                    _numTypesValid = NR_FLOAT;
                }
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            case 27: // Float64
                _numberDouble = Double.longBitsToDouble(_decode64Bits());
                _numTypesValid = NR_DOUBLE;
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            case 31: // Break
                if (_streamReadContext.inArray()) {
                    if (!_streamReadContext.hasExpectedLength()) {
                        _streamReadContext = _streamReadContext.getParent();
                        return (_currToken = JsonToken.END_ARRAY);
                    }
                }
                // Object end-marker can't occur here
                _reportUnexpectedBreak();
            }
            return (_currToken = _decodeSimpleValue(lowBits, ch));
        }
    }

    protected String _numberToName(int ch, boolean neg) throws JacksonException
    {
        final int lowBits = ch & 0x1F;
        int i;
        if (lowBits <= 23) {
            i = lowBits;
        } else {
            switch (lowBits) {
            case 24:
                i = _decode8Bits();
                break;
            case 25:
                i = _decode16Bits();
                break;
            case 26:
                i = _decode32Bits();
                // [dataformats-binary#269] (and earlier [dataformats-binary#30]),
                // got some edge case to consider
                if (i < 0) {
                    long l;
                    if (neg) {
                        long unsignedBase = (long) i & 0xFFFFFFFFL;
                        l = -unsignedBase - 1L;
                    } else {
                        l = (long) i;
                        l = l & 0xFFFFFFFFL;
                    }
                    return String.valueOf(l);
                }
                break;
            case 27:
                {
                    long l = _decode64Bits();
                    if (neg) {
                        l = -l - 1L;
                    }
                    return String.valueOf(l);
                }
            default:
                throw _constructReadException("Invalid length indicator for ints (%d), token 0x%s",
                        lowBits, Integer.toHexString(ch));
            }
        }
        if (neg) {
            i = -i - 1;
        }
        return String.valueOf(i);
    }

    protected JsonToken _handleTaggedBinary(int tag) throws JacksonException
    {
        // For now all we should get is BigInteger
        boolean neg;
        if (tag == TAG_BIGNUM_POS) {
            neg = false;
        } else  if (tag == TAG_BIGNUM_NEG) {
            neg = true;
        } else {
            // 12-May-2016, tatu: Since that's all we know, let's otherwise
            //   just return default Binary data marker
            return (_currToken = JsonToken.VALUE_EMBEDDED_OBJECT);
        }

        // First: get the data
        _finishToken();

        // [dataformats-binar#261]: handle this special case
        if (_binaryValue.length == 0) {
            _numberBigInt = BigInteger.ZERO;
        } else {
            BigInteger nr = new BigInteger(_binaryValue);
            if (neg) {
                nr = nr.negate();
            }
            _numberBigInt = nr;
        }
        _numTypesValid = NR_BIGINT;
        _tagValue = -1;
        return (_currToken = JsonToken.VALUE_NUMBER_INT);
    }

    protected JsonToken _handleTaggedArray(int tag, int len) throws JacksonException
    {
        // For simplicity, let's create matching array context -- in perfect
        // world that wouldn't be necessarily, but in this one there are
        // some constraints that make it necessary
        _streamReadContext = _streamReadContext.createChildArrayContext(len);

        // BigDecimal is the only thing we know for sure
        if (tag != CBORConstants.TAG_DECIMAL_FRACTION) {
            return (_currToken = JsonToken.START_ARRAY);
        }
        _currToken = JsonToken.START_ARRAY;

        // but has to have length of 2; otherwise we have a problem...
        if (len != 2) {
            _reportError("Unexpected array size ("+len+") for tagged 'bigfloat' value; should have exactly 2 number elements");
        }
        // and then use recursion to get values
        // First: exponent, which MUST be a simple integer value
        if (!_checkNextIsIntInArray("bigfloat")) {
            _reportError("Unexpected token ("+currentToken()+") as the first part of 'bigfloat' value: should get VALUE_NUMBER_INT");
        }
        // 27-Nov-2019, tatu: As per [dataformats-binary#139] need to change sign here
        int exp = -getIntValue();

        // Should get an integer value; int/long/BigInteger
        if (!_checkNextIsIntInArray("bigfloat")) {
            _reportError("Unexpected token ("+currentToken()+") as the second part of 'bigfloat' value: should get VALUE_NUMBER_INT");
        }

        // important: check number type here
        BigDecimal dec;
        NumberType numberType = getNumberType();
        if (numberType == NumberType.BIG_INTEGER) {
            dec = new BigDecimal(getBigIntegerValue(), exp);
        } else  {
            dec = BigDecimal.valueOf(getLongValue(), exp);
        }

        // but verify closing END_ARRAY here, as this will now override current token
        if (!_checkNextIsEndArray()) {
            _reportError("Unexpected token ("+currentToken()+") after 2 elements of 'bigfloat' value");
        }

        // which needs to be reset here
        _numberBigDecimal = dec;
        _numTypesValid = NR_BIGDECIMAL;
        return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
    }

    /**
     * Heavily simplified method that does a subset of what {@code nextToken()} does to basically
     * only (1) determine that we are getting {@code JsonToken.VALUE_NUMBER_INT} (if not,
     * return with no processing) and (2) if so, prepare state so that number accessor
     * method will work).
     *<p>
     * Note that in particular this method DOES NOT reset state that {@code nextToken()} would do,
     * but will change current token type to allow access.
     */
    protected final boolean _checkNextIsIntInArray(final String typeDesc) throws JacksonException
    {
        // We know we are in array, with length prefix so:
        if (!_streamReadContext.expectMoreValues()) {
            _tagValue = -1;
            _streamReadContext = _streamReadContext.getParent();
            _currToken = JsonToken.END_ARRAY;
            return false;
        }

        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _eofAsNextToken();
                return false;
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        int type = (ch >> 5);
        int lowBits = ch & 0x1F;

        // 01-Nov-2019, tatu: We may actually need tag so decode it, but do not assign
        //   (that'd override tag we already have)
        int tagValue = -1;
        if (type == 6) {
            tagValue = _decodeTag(lowBits);
            if ((_inputPtr >= _inputEnd) && !loadMore()) {
                _eofAsNextToken();
                return false;
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            type = (ch >> 5);
            lowBits = ch & 0x1F;
        }

        switch (type) {
        case 0: // positive int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = lowBits;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = _decode8Bits();
                    break;
                case 1:
                    _numberInt = _decode16Bits();
                    break;
                case 2:
                    {
                        int v = _decode32Bits();
                        if (v >= 0) {
                            _numberInt = v;
                        } else {
                            long l = (long) v;
                            _numberLong = l & 0xFFFFFFFFL;
                            _numTypesValid = NR_LONG;
                        }
                    }
                    break;
                case 3:
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = l;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigPositive(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            _currToken = JsonToken.VALUE_NUMBER_INT;
            return true;
        case 1: // negative int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = -lowBits - 1;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = -_decode8Bits() - 1;
                    break;
                case 1:
                    _numberInt = -_decode16Bits() - 1;
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v < 0) {
                            long unsignedBase = (long) v & 0xFFFFFFFFL;
                            _numberLong = -unsignedBase - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberInt = -v - 1;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = -l - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigNegative(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            _currToken = JsonToken.VALUE_NUMBER_INT;
            return true;

        case 2: // byte[]
            // ... but we only really care about very specific case of `BigInteger`
            if (tagValue < 0) {
                break;
            }
            _typeByte = ch;
            _tokenIncomplete = true;
            _currToken = _handleTaggedBinary(tagValue);
            return (_currToken == JsonToken.VALUE_NUMBER_INT);

        case 6: // another tag; not allowed
            _reportError("Multiple tags not allowed per value (first tag: "+tagValue+")");
        }

        // Important! Need to push back the last byte read (but not consumed)
        --_inputPtr;
        // and now it is safe to decode next token, too
        nextToken();
        return false;
    }

    protected final boolean _checkNextIsEndArray() throws JacksonException
    {
        // We know we are in array, with length prefix, and this is where we should be:
        if (!_streamReadContext.expectMoreValues()) {
            _tagValue = -1;
            _streamReadContext = _streamReadContext.getParent();
            _currToken = JsonToken.END_ARRAY;
            return true;
        }

        // But while we otherwise could bail out we should check what follows for better
        // error reporting... yet we ALSO must avoid direct call to `nextToken()` to avoid
        // [dataformats-binary#185]
        int ch = _inputBuffer[_inputPtr++];
        int type = (ch >> 5) & 0x7;

        // No use for tag but removing it is necessary
        int tagValue = -1;
        if (type == 6) {
            tagValue = _decodeTag(ch & 0x1F);
            if ((_inputPtr >= _inputEnd) && !loadMore()) {
                _eofAsNextToken();
                return false;
            }
            ch = _inputBuffer[_inputPtr++];
            type = (ch >> 5) & 0x7;
            // including but not limited to nested tags (which we do not allow)
            if (type == 6) {
                _reportError("Multiple tags not allowed per value (first tag: "+tagValue+")");
            }
        }
        // and that's what we need to do for safety; now can drop to generic handling:
        
        // Important! Need to push back the last byte read (but not consumed)
        --_inputPtr;
        return nextToken() == JsonToken.END_ARRAY; // should never match
    }

    // base impl is fine:
    //public String currentName() throws JacksonException

    /**
     * Method for forcing full read of current token, even if it might otherwise
     * only be read if data is accessed via {@link #getText} and similar methods.
     */
    @Override
    public void finishToken() throws JacksonException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
    }

    /*
    /**********************************************************
    /* Public API, traversal, optimized: nextName
    /**********************************************************
     */

    @Override
    public String nextName() throws JacksonException
    {
        if (_streamReadContext.inObject() && _currToken != JsonToken.PROPERTY_NAME) {
            _numTypesValid = NR_UNKNOWN;
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            _tokenInputTotal = _currInputProcessed + _inputPtr;
            _binaryValue = null;
            _tagValue = -1;
            // completed the whole Object?
            if (!_streamReadContext.expectMoreValues()) {
                _streamReadContext = _streamReadContext.getParent();
                _currToken = JsonToken.END_OBJECT;
                return null;
            }
            // inlined "_decodeFieldName()"

            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _eofAsNextToken();
                }
            }
            final int ch = _inputBuffer[_inputPtr++];
            final int type = ((ch >> 5) & 0x7);

            // offline non-String cases, as they are expected to be rare
            if (type != CBORConstants.MAJOR_TYPE_TEXT) {
                if (ch == -1) { // end-of-object, common
                    if (!_streamReadContext.hasExpectedLength()) {
                        _streamReadContext = _streamReadContext.getParent();
                        _currToken = JsonToken.END_OBJECT;
                        return null;
                    }
                    _reportUnexpectedBreak();
                }
                String name = _decodeNonStringName(ch);
                _currToken = JsonToken.PROPERTY_NAME;
                return name;
            }
            final int lenMarker = ch & 0x1F;
            String name;
            if (lenMarker <= 23) {
                if (lenMarker == 0) {
                    name = "";
                } else {
                    if ((_inputEnd - _inputPtr) < lenMarker) {
                        _loadToHaveAtLeast(lenMarker);
                    }
                    if (_symbolsCanonical) {
                        name = _findDecodedFromSymbols(lenMarker);
                        if (name != null) {
                            _inputPtr += lenMarker;
                        } else {
                            name = _decodeContiguousName(lenMarker);
                            name = _addDecodedToSymbols(lenMarker, name);
                        }
                    } else {
                        name = _decodeContiguousName(lenMarker);
                    }
                }
            } else {
                final int actualLen = _decodeExplicitLength(lenMarker);
                if (actualLen < 0) {
                    name = _decodeChunkedName();
                } else {
                    name = _decodeLongerName(actualLen);
                }
            }
            _streamReadContext.setCurrentName(name);
            _currToken = JsonToken.PROPERTY_NAME;
            return name;
        }
        // otherwise just fall back to default handling; should occur rarely
        return (nextToken() == JsonToken.PROPERTY_NAME) ? currentName() : null;
    }

    @Override
    public boolean nextName(SerializableString str) throws JacksonException
    {
        // Two parsing modes; can only succeed if expecting field name, so handle that first:
        if (_streamReadContext.inObject() && _currToken != JsonToken.PROPERTY_NAME) {
            _numTypesValid = NR_UNKNOWN;
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            _tokenInputTotal = _currInputProcessed + _inputPtr;
            _binaryValue = null;
            _tagValue = -1;
            // completed the whole Object?
            if (!_streamReadContext.expectMoreValues()) {
                _streamReadContext = _streamReadContext.getParent();
                _currToken = JsonToken.END_OBJECT;
                return false;
            }
            byte[] nameBytes = str.asQuotedUTF8();
            final int byteLen = nameBytes.length;
            // fine; require room for up to 2-byte marker, data itself
            int ptr = _inputPtr;
            if ((ptr + byteLen + 1) < _inputEnd) {
                final int ch = _inputBuffer[ptr++];
                // only handle usual textual type
                if (((ch >> 5) & 0x7) == CBORConstants.MAJOR_TYPE_TEXT) {
                    int len = ch & 0x1F;
                    if (len <= 24) {
                        if (len == 23) {
                            len = _inputBuffer[ptr++] & 0xFF;
                        }
                        if (len == byteLen) {
                            for (int i = 0; i < len; ++i) {
                                if (nameBytes[i] != _inputBuffer[ptr+i]) {
                                    return str.getValue().equals(nextName());
                                }
                            }
                            _inputPtr = ptr + byteLen;
                            _streamReadContext.setCurrentName(str.getValue());
                            _currToken = JsonToken.PROPERTY_NAME;
                            return true;
                        }
                    }
                }
            }
        }
        // otherwise just fall back to default handling; should occur rarely
        return str.getValue().equals(nextName());
    }

    @Override
    public int nextNameMatch(PropertyNameMatcher matcher) throws JacksonException
    {
        // Two parsing modes; can only succeed if expecting field name, so handle that first:
        if ((_currToken == JsonToken.PROPERTY_NAME) || !_streamReadContext.inObject()) {
            nextToken();
            return PropertyNameMatcher.MATCH_ODD_TOKEN;
        }

        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;
        _tagValue = -1;
        // completed the whole Object?
        if (!_streamReadContext.expectMoreValues()) {
            _streamReadContext = _streamReadContext.getParent();
            _currToken = JsonToken.END_OBJECT;
            return PropertyNameMatcher.MATCH_END_OBJECT;
        }
        // inlined "_decodeFieldName()"

        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        final int ch = _inputBuffer[_inputPtr++];
        final int type = ((ch >> 5) & 0x7);

        // offline non-String cases, as they are expected to be rare
        if (type != CBORConstants.MAJOR_TYPE_TEXT) {
            if (ch == -1) { // end-of-object, common
                if (!_streamReadContext.hasExpectedLength()) {
                    _streamReadContext = _streamReadContext.getParent();
                    _currToken = JsonToken.END_OBJECT;
                    return PropertyNameMatcher.MATCH_END_OBJECT;
                }
                _reportUnexpectedBreak();
            }
            return _nextNameNonText(matcher, ch);
        }
        final int lenMarker = ch & 0x1F;
        // also off-line handling of long(er) names
        if (lenMarker > 23) {
            return _nextNameLong(matcher, lenMarker);
        }
        if (lenMarker == 0) {
            return _nextNameEmpty(matcher);
        }
        int match = _nextFieldOptimized(matcher, lenMarker);
        if (match < 0) { // miss...
            // but if not matched by matcher we got, need to still decode
            return _nextFieldDecodeAndAdd(matcher, lenMarker);
        }
        _inputPtr += lenMarker;
        final String name = matcher.nameLookup()[match];
        _streamReadContext.setCurrentName(name);
        _currToken = JsonToken.PROPERTY_NAME;
        return match;
    }

    private int _nextFieldDecodeAndAdd(PropertyNameMatcher matcher, int len) throws JacksonException
    {
        // 27-Nov-2017, tatu: May already be in main shared symbol table, need to check...
        String name;
        final int qlen = (len + 3) >> 2;
        switch (qlen) {
        case 1:
            name = _symbols.findName(_quad1);
            break;
        case 2:
            name = _symbols.findName(_quad1, _quad2);
            break;
        case 3:
            name = _symbols.findName(_quad1, _quad2, _quad3);
            break;
        default:
            name = _symbols.findName(_quadBuffer, qlen);
        }
        if (name == null) {
            name = _decodeContiguousName(len);
            name = _addDecodedToSymbols(len, name);
        } else {
            _inputPtr += len;
        }
        _streamReadContext.setCurrentName(name);
        _currToken = JsonToken.PROPERTY_NAME;
        // 07-Feb-2017, tatu: May actually have match in non-quad part (esp. for case-insensitive)
        return matcher.matchName(name);
    }

    private int _nextNameNonText(PropertyNameMatcher matcher, int ch) throws JacksonException
    {
        String name = _decodeNonStringName(ch); // NOTE: sets current name too
        _currToken = JsonToken.PROPERTY_NAME;
        /// 15-Nov-2017, tatu: Is this correct? Copied from `nextName()` but...
        return matcher.matchName(name);
    }

    // For presumable rare case of ""
    private int _nextNameEmpty(PropertyNameMatcher matcher) throws JacksonException {
        _streamReadContext.setCurrentName("");
        _currToken = JsonToken.PROPERTY_NAME;
        return matcher.matchName("");
    }

    private int _nextNameLong(PropertyNameMatcher matcher, int lenMarker) throws JacksonException
    {
        final int actualLen = _decodeExplicitLength(lenMarker);
        String name;
        if (actualLen < 0) {
            name = _decodeChunkedName();
        } else {
            name = _decodeLongerName(actualLen);
        }
        _streamReadContext.setCurrentName(name);
        _currToken = JsonToken.PROPERTY_NAME;
        return matcher.matchName(name);
    }

    private final int _nextFieldOptimized(PropertyNameMatcher matcher, final int len) throws JacksonException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        // First: maybe we already have this name decoded?
        if (len < 5) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            int q = inBuf[inPtr] & 0xFF;
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    if (len > 3) {
                        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q;
            return matcher.matchByQuad(q);
        }

        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;

        // First quadbyte is easy
        int q1 = (inBuf[inPtr++] & 0xFF);
        q1 =  (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 =  (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 =  (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        
        if (len < 9) {
            int q2 = (inBuf[inPtr++] & 0xFF);
            int left = len - 5;
            if (left > 0) {
                q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            return matcher.matchByQuad(q1, q2);
        }

        int q2 = (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);

        if (len < 13) {
            int q3 = (inBuf[inPtr++] & 0xFF);
            int left = len - 9;
            if (left > 0) {
                q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            _quad3 = q3;
            return matcher.matchByQuad(q1, q2, q3);
        }
        return _nextFieldFromSymbolsLong(matcher, len, q1, q2);
    }

    /**
     * Method for locating names longer than 8 bytes (in UTF-8)
     */
    private final int _nextFieldFromSymbolsLong(PropertyNameMatcher matcher, 
            int len, int q1, int q2) throws JacksonException
    {
        // first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, bufLen);
            }
        }
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;
        
        // then decode, full quads first
        int offset = 2;
        int inPtr = _inputPtr+8;
        len -= 8;
        
        final byte[] inBuf = _inputBuffer;
        do {
            int q = (inBuf[inPtr++] & 0xFF);
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            _quadBuffer[offset++] = q;
        } while ((len -= 4) > 3);
        // and then leftovers
        if (len > 0) {
            int q = inBuf[inPtr] & 0xFF;
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                }
            }
            _quadBuffer[offset++] = q;
        }
        return matcher.matchByQuad(_quadBuffer, offset);
//        return _symbols.findName(_quadBuffer, offset);
    }

    /*
    /**********************************************************
    /* Public API, traversal, optimized: nextXxxValue
    /**********************************************************
     */
    
    @Override
    public String nextTextValue() throws JacksonException
    {
        _numTypesValid = NR_UNKNOWN;
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;
        _tagValue = -1;

        if (_streamReadContext.inObject()) {
            if (_currToken != JsonToken.PROPERTY_NAME) {
                _tagValue = -1;
                // completed the whole Object?
                if (!_streamReadContext.expectMoreValues()) {
                    _streamReadContext = _streamReadContext.getParent();
                    _currToken = JsonToken.END_OBJECT;
                    return null;
                }
                _currToken = _decodePropertyName();
                return null;
            }
        } else {
            if (!_streamReadContext.expectMoreValues()) {
                _tagValue = -1;
                _streamReadContext = _streamReadContext.getParent();
                _currToken = JsonToken.END_ARRAY;
                return null;
            }
        }
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _eofAsNextToken();
                return null;
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        int type = (ch >> 5);
        int lowBits = ch & 0x1F;

        // One special case: need to consider tag as prefix first:
        if (type == 6) {
            _tagValue = Integer.valueOf(_decodeTag(lowBits));
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _eofAsNextToken();
                    return null;
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            type = (ch >> 5);
            lowBits = ch & 0x1F;
        } else {
            _tagValue = -1;
        }

        switch (type) {
        case 0: // positive int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = lowBits;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = _decode8Bits();
                    break;
                case 1:
                    _numberInt = _decode16Bits();
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v < 0) {
                            long l = (long) v;
                            _numberLong = l & 0xFFFFFFFFL;
                            _numTypesValid = NR_LONG;
                        } else{
                            _numberInt = v;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = l;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigPositive(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            _currToken = JsonToken.VALUE_NUMBER_INT;
            return null;
        case 1: // negative int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = -lowBits - 1;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = -_decode8Bits() - 1;
                    break;
                case 1:
                    _numberInt = -_decode16Bits() - 1;
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v < 0) {
                            long unsignedBase = (long) v & 0xFFFFFFFFL;
                            _numberLong = -unsignedBase - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberInt = -v - 1;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                {
                    long l = _decode64Bits();
                    if (l >= 0L) {
                        _numberLong = l;
                        _numTypesValid = NR_LONG;
                    } else {
                        _numberBigInt = _bigNegative(l);
                        _numTypesValid = NR_BIGINT;
                    }
                }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            _currToken = JsonToken.VALUE_NUMBER_INT;
            return null;

        case 2: // byte[]
            _typeByte = ch;
            _tokenIncomplete = true;
            _currToken = JsonToken.VALUE_EMBEDDED_OBJECT;
            return null;

        case 3: // String
            _typeByte = ch;
            _tokenIncomplete = true;
            _currToken = JsonToken.VALUE_STRING;
            return _finishTextToken(ch);

        case 4: // Array
            _currToken = JsonToken.START_ARRAY;
            {
                int len = _decodeExplicitLength(lowBits);
                _streamReadContext = _streamReadContext.createChildArrayContext(len);
            }
            return null;

        case 5: // Object
            _currToken = JsonToken.START_OBJECT;
            {
                int len = _decodeExplicitLength(lowBits);
                _streamReadContext = _streamReadContext.createChildObjectContext(len);
            }
            return null;

        case 6: // another tag; not allowed
            _reportError("Multiple tags not allowed per value (first tag: "+_tagValue+")");
            
        case 7:
        default: // misc: tokens, floats
            switch (lowBits) {
            case 20:
                _currToken = JsonToken.VALUE_FALSE;
                return null;
            case 21:
                _currToken = JsonToken.VALUE_TRUE;
                return null;
            case 22:
                _currToken = JsonToken.VALUE_NULL;
                return null;
            case 23:
                _currToken = _decodeUndefinedValue();
                return null;

            case 25: // 16-bit float... 
                // As per [http://stackoverflow.com/questions/5678432/decompressing-half-precision-floats-in-javascript]
                {
                    _numberFloat = _decodeHalfSizeFloat();
                    _numTypesValid = NR_FLOAT;
                }
                _currToken = JsonToken.VALUE_NUMBER_FLOAT;
                return null;
            case 26: // Float32
                {
                    _numberFloat = Float.intBitsToFloat(_decode32Bits());
                    _numTypesValid = NR_FLOAT;
                }
                _currToken = JsonToken.VALUE_NUMBER_FLOAT;
                return null;
            case 27: // Float64
                _numberDouble = Double.longBitsToDouble(_decode64Bits());
                _numTypesValid = NR_DOUBLE;
                _currToken = JsonToken.VALUE_NUMBER_FLOAT;
                return null;
            case 31: // Break
                if (_streamReadContext.inArray()) {
                    if (!_streamReadContext.hasExpectedLength()) {
                        _streamReadContext = _streamReadContext.getParent();
                        _currToken = JsonToken.END_ARRAY;
                        return null;
                    }
                }
                // Object end-marker can't occur here
                _reportUnexpectedBreak();
            }
            _currToken = _decodeSimpleValue(lowBits, ch);
            return null;
        }
    }

    @Override
    public int nextIntValue(int defaultValue) throws JacksonException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getIntValue();
        }
        return defaultValue;
    }

    @Override
    public long nextLongValue(long defaultValue) throws JacksonException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getLongValue();
        }
        return defaultValue;
    }

    @Override
    public Boolean nextBooleanValue() throws JacksonException
    {
        JsonToken t = nextToken();
        if (t == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (t == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        return null;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
     */

    /**
     * Method for accessing textual representation of the current event;
     * if no current event (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any event.
     */
    @Override    
    public String getText() throws JacksonException
    {
        JsonToken t = _currToken;
        if (_tokenIncomplete) {
            if (t == JsonToken.VALUE_STRING) {
                return _finishTextToken(_typeByte);
            }
        }
        if (t == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        if (t == null) { // null only before/after document
            return null;
        }
        if (t == JsonToken.PROPERTY_NAME) {
            return _streamReadContext.currentName();
        }
        if (t.isNumeric()) {
            return getNumberValue().toString();
        }
        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws JacksonException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            if (_currToken == JsonToken.VALUE_STRING) {
                return _textBuffer.getTextBuffer();
            }
            if (_currToken == JsonToken.PROPERTY_NAME) {
                return _streamReadContext.currentName().toCharArray();
            }
            if ((_currToken == JsonToken.VALUE_NUMBER_INT)
                    || (_currToken == JsonToken.VALUE_NUMBER_FLOAT)) {
                return getNumberValue().toString().toCharArray();
            }
            return _currToken.asCharArray();
        }
        return null;
    }

    @Override    
    public int getTextLength() throws JacksonException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            if (_currToken == JsonToken.VALUE_STRING) {
                return _textBuffer.size();                
            }
            if (_currToken == JsonToken.PROPERTY_NAME) {
                return _streamReadContext.currentName().length();
            }
            if ((_currToken == JsonToken.VALUE_NUMBER_INT)
                    || (_currToken == JsonToken.VALUE_NUMBER_FLOAT)) {
                return getNumberValue().toString().length();
            }
            return _currToken.asCharArray().length;
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws JacksonException {
        return 0;
    }

    @Override
    public String getValueAsString() throws JacksonException
    {
        // inlined 'getText()' for common case of having String
        if (_tokenIncomplete) {
            if (_currToken == JsonToken.VALUE_STRING) {
                return _finishTextToken(_typeByte);
            }
        }
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
            return null;
        }
        return getText();
    }

    @Override
    public String getValueAsString(String defaultValue) throws JacksonException
    {
        if (_currToken != JsonToken.VALUE_STRING) {
            if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
                return defaultValue;
            }
        }
        return getText();
    }

    @Override
    public int getText(Writer writer) throws JacksonException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        try {
            JsonToken t = _currToken;
            if (t == JsonToken.VALUE_STRING) {
                return _textBuffer.contentsToWriter(writer);
            }
            if (t == JsonToken.PROPERTY_NAME) {
                String n = _streamReadContext.currentName();
                writer.write(n);
                return n.length();
            }
            if (t != null) {
                if (t.isNumeric()) {
                    return _textBuffer.contentsToWriter(writer);
                }
                char[] ch = t.asCharArray();
                writer.write(ch);
                return ch.length;
            }
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return 0;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, binary
    /**********************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws JacksonException
    {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            if (_tokenIncomplete) {
                _finishToken();
            }
        } else  if (_currToken == JsonToken.VALUE_STRING) {
            return _getBinaryFromString(b64variant);
        } else {
            throw _constructReadException(
"Current token (%s) not VALUE_EMBEDDED_OBJECT or VALUE_STRING, can not access as binary",
                    currentToken());
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject() throws JacksonException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws JacksonException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT) {
            if (_currToken == JsonToken.VALUE_STRING) {
                // 26-Jun-2021, tatu: Not optimized; could make streaming if we
                //    really want in future
                final byte[] b = _getBinaryFromString(b64variant);
                final int len = b.length;
                try {
                    out.write(b, 0, len);
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
                return len;
            }
            throw _constructReadException(
"Current token (%s) not VALUE_EMBEDDED_OBJECT or VALUE_STRING, can not access as binary",
                    currentToken());
        }
        if (!_tokenIncomplete) { // someone already decoded or read
            if (_binaryValue == null) { // if this method called twice in a row
                return 0;
            }
            final int len = _binaryValue.length;
            try {
                out.write(_binaryValue, 0, len);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            return len;
        }

        _tokenIncomplete = false;
        int len = _decodeExplicitLength(_typeByte & 0x1F);
        if (len >= 0) { // non-chunked
            return _readAndWriteBytes(out, len);
        }
        // Chunked...
        int total = 0;
        while (true) {
            len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_BYTES);
            if (len < 0) {
                return total;
            }
            total += _readAndWriteBytes(out, len);
        }
    }

    private int _readAndWriteBytes(OutputStream out, final int total) throws JacksonException
    {
        int left = total;
        while (left > 0) {
            int avail = _inputEnd - _inputPtr;
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportIncompleteBinaryRead(total, total-left);
                }
                avail = _inputEnd - _inputPtr;
            }
            int count = Math.min(avail, left);
            try {
                out.write(_inputBuffer, _inputPtr, count);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            _inputPtr += count;
            left -= count;
        }
        _tokenIncomplete = false;
        return total;
    }

    // @since 2.13
    private final byte[] _getBinaryFromString(Base64Variant variant) throws JacksonException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_binaryValue == null) {
            // 26-Jun-2021, tatu: Copied from ParserBase
            ByteArrayBuilder builder = _getByteArrayBuilder();
            _decodeBase64(getText(), builder, variant);
            _binaryValue = builder.toByteArray();
        }
        return _binaryValue;
    }

    /*
    /**********************************************************************
    /* Numeric accessors of public API
    /**********************************************************************
     */

    @Override // since 2.9
    public boolean isNaN() {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                // 10-Mar-2017, tatu: Alas, `Double.isFinite(d)` only added in JDK 8
                double d = _numberDouble;
                return Double.isNaN(d) || Double.isInfinite(d);
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                float f = _numberFloat;
                return Float.isNaN(f) || Float.isInfinite(f);
            }
        }
        return false;
    }

    @Override
    public Number getNumberValue() throws JacksonException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        // Separate types for int types
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return _numberInt;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return _numberLong;
            }
            if ((_numTypesValid & NR_BIGINT) != 0) {
                return _numberBigInt;
            }
            // Shouldn't get this far but if we do
            return _numberBigDecimal;
        }
    
        // And then floating point types. But here optimal type
        // needs to be big decimal, to avoid losing any data?
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return _numberBigDecimal;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return _numberDouble;
        }
        if ((_numTypesValid & NR_FLOAT) == 0) { // sanity check
            _throwInternal();
        }
        return _numberFloat;
    }

    @Override // @since 2.12 -- for (most?) binary formats exactness guaranteed anyway
    public final Number getNumberValueExact() throws JacksonException {
        return getNumberValue();
    }

    @Override
    public NumberType getNumberType() throws JacksonException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return NumberType.INT;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return NumberType.LONG;
            }
            return NumberType.BIG_INTEGER;
        }
    
        /* And then floating point types. Here optimal type
         * needs to be big decimal, to avoid losing any data?
         * However... using BD is slow, so let's allow returning
         * double as type if no explicit call has been made to access
         * data as BD?
         */
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return NumberType.BIG_DECIMAL;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return NumberType.DOUBLE;
        }
        return NumberType.FLOAT;
    }

//    public int getIntValue() throws JacksonException

//    public long getLongValue() throws JacksonException

//    public BigInteger getBigIntegerValue() throws JacksonException

    @Override
    public float getFloatValue() throws JacksonException
    {
        if ((_numTypesValid & NR_FLOAT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_FLOAT);
            }
            if ((_numTypesValid & NR_FLOAT) == 0) {
                convertNumberToFloat();
            }
        }
        // Bounds/range checks would be tricky here, so let's not bother even trying...
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value ("+getText()+") out of range of Java float");
        }
        */
        return _numberFloat;
    }

//    public double getDoubleValue() throws JacksonException

//    public BigDecimal getDecimalValue() throws JacksonException

    // Not needed since no lazy decoding for numbers
    @Override
    protected void _parseNumericValue(int expType) throws JacksonException {
        _throwInternal();
    }

    // Not needed since no lazy decoding for numbers
    @Override
    protected int _parseIntValue() throws JacksonException {
        _throwInternal();
        return 0;
    }

    /*
    /**********************************************************************
    /* Numeric conversions
    /**********************************************************************
     */    

    protected void _checkNumericValue(int expType) throws JacksonException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token ("+currentToken()+") not numeric, can not use numeric value accessors");
    }

    @Override // due to addition of Float as type
    protected void convertNumberToInt() throws JacksonException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportError("Numeric value ("+getText()+") out of range of int");
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0 
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                _reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                _reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                _reportOverflowInt();
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                _reportOverflowInt();
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }
    
    @Override // due to addition of Float as type
    protected void convertNumberToLong() throws JacksonException
    {
        if ((_numTypesValid & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0 
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                _reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                _reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                _reportOverflowInt();
            }
            _numberLong = (long) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                _reportOverflowLong();
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }

    @Override // due to addition of Float as type
    protected void convertNumberToBigInteger() throws JacksonException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            _numberBigInt = _numberBigDecimal.toBigInteger();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberInt);
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberDouble).toBigInteger();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberFloat).toBigInteger();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGINT;
    }

    // Base class does not have this one...
    protected void convertNumberToFloat() throws JacksonException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberFloat = _numberBigDecimal.floatValue();
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberFloat = _numberBigInt.floatValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberFloat = (float) _numberDouble;
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberFloat = (float) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberFloat = (float) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_FLOAT;
    }

    @Override // due to addition of Float as type
    protected void convertNumberToDouble() throws JacksonException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberDouble = _numberBigDecimal.doubleValue();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberDouble = (double) _numberFloat;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberDouble = _numberBigInt.doubleValue();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberDouble = (double) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberDouble = (double) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_DOUBLE;
    }

    @Override // due to addition of Float as type
    protected void convertNumberToBigDecimal() throws JacksonException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & (NR_DOUBLE | NR_FLOAT)) != 0) {
            // Let's parse from String representation, to avoid rounding errors that
            //non-decimal floating operations would incur
            _numberBigDecimal = NumberInput.parseBigDecimal(getText());
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberBigInt);
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberInt);
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGDECIMAL;
    }

    /*
    /**********************************************************************
    /* Internal methods, secondary parsing
    /**********************************************************************
     */

    /**
     * Method called to finish parsing of a token so that token contents
     * are retriable
     */
    protected void _finishToken() throws JacksonException
    {
        _tokenIncomplete = false;
        int ch = _typeByte;
        final int type = ((ch >> 5) & 0x7);
        ch &= 0x1F;

        // Either String or byte[]
        if (type != CBORConstants.MAJOR_TYPE_TEXT) {
            if (type == CBORConstants.MAJOR_TYPE_BYTES) {
                _binaryValue = _finishBytes(_decodeExplicitLength(ch));
                return;
            }
            // should never happen so
            _throwInternal();
        }

        // String value, decode
        final int len = _decodeExplicitLength(ch);

        if (len <= 0) {
            if (len < 0) {
                _finishChunkedText();
            } else {
                _textBuffer.resetWithEmpty();
            }
            return;
        }
        // 29-Jan-2021, tatu: as per [dataformats-binary#238] must keep in mind that
        //    the longest individual unit is 4 bytes (surrogate pair) so we
        //    actually need len+3 bytes to avoid bounds checks
        final int needed = len + 3;
        final int available = _inputEnd - _inputPtr;

        if ((available >= needed)
                // if not, could we read? NOTE: we do not require it, just attempt to read
                    || ((_inputBuffer.length >= needed)
                            && _tryToLoadToHaveAtLeast(needed))) {
                _finishShortText(len);
                return;
        }
        // If not enough space, need handling similar to chunked
        _finishLongText(len);
    }

    protected String _finishTextToken(int ch) throws JacksonException
    {
        _tokenIncomplete = false;
        final int type = ((ch >> 5) & 0x7);
        ch &= 0x1F;

        // sanity check
        if (type != CBORConstants.MAJOR_TYPE_TEXT) {
            // should never happen so
            _throwInternal();
        }

        // String value, decode
        final int len = _decodeExplicitLength(ch);
        if (len <= 0) {
            if (len == 0) {
                _textBuffer.resetWithEmpty();
                return "";
            }
            _finishChunkedText();
            return _textBuffer.contentsAsString();
        }
        // 29-Jan-2021, tatu: as per [dataformats-binary#238] must keep in mind that
        //    the longest individual unit is 4 bytes (surrogate pair) so we
        //    actually need len+3 bytes to avoid bounds checks

        // 19-Mar-2021, tatu: [dataformats-binary#259] shows the case where length
        //    we get is Integer.MAX_VALUE, leading to overflow. Could change values
        //    to longs but simpler to truncate "needed" (will never pass following test
        //    due to inputBuffer never being even close to that big).

        final int needed = Math.max(len + 3, len);
        final int available = _inputEnd - _inputPtr;

        if ((available >= needed)
            // if not, could we read? NOTE: we do not require it, just attempt to read
                || ((_inputBuffer.length >= needed)
                        && _tryToLoadToHaveAtLeast(needed))) {
            return _finishShortText(len);
        }
        // If not enough space, need handling similar to chunked
        _finishLongText(len);
        return _textBuffer.contentsAsString();
    }

    private final String _finishShortText(int len) throws JacksonException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }

        int outPtr = 0;
        int inPtr = _inputPtr;
        _inputPtr += len;
        final byte[] inputBuf = _inputBuffer;

        // Let's actually do a tight loop for ASCII first:
        final int end = inPtr + len;

        int i;
        while ((i = inputBuf[inPtr]) >= 0) {
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                return _textBuffer.setCurrentAndReturn(outPtr);
            }
        }
        final int[] codes = UTF8_UNIT_CODES;
        do {
            i = inputBuf[inPtr++] & 0xFF;
            switch (codes[i]) {
            case 0:
                break;
            case 1:
                {
                    final int c2 = inputBuf[inPtr++];
                    if ((c2 & 0xC0) != 0x080) {
                        _reportInvalidOther(c2 & 0xFF, inPtr);
                    }
                    i = ((i & 0x1F) << 6) | (c2 & 0x3F);
                }
                break;
            case 2:
                {
                    final int c2 = inputBuf[inPtr++];
                    if ((c2 & 0xC0) != 0x080) {
                        _reportInvalidOther(c2 & 0xFF, inPtr);
                    }
                    final int c3 = inputBuf[inPtr++];
                    if ((c3 & 0xC0) != 0x080) {
                        _reportInvalidOther(c3 & 0xFF, inPtr);
                    }
                    i = ((i & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                }
                break;
            case 3:
                // 30-Jan-2021, tatu: TODO - validate these too?
                i = ((i & 0x07) << 18)
                    | ((inputBuf[inPtr++] & 0x3F) << 12)
                    | ((inputBuf[inPtr++] & 0x3F) << 6)
                    | (inputBuf[inPtr++] & 0x3F);
                // note: this is the codepoint value; need to split, too
                i -= 0x10000;
                outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                i = 0xDC00 | (i & 0x3FF);
                break;
            default: // invalid
                _reportInvalidInitial(i);
            }
            outBuf[outPtr++] = (char) i;
        } while (inPtr < end);
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    private final void _finishLongText(int len) throws JacksonException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = UTF8_UNIT_CODES;
        int outEnd = outBuf.length;

        while (--len >= 0) {
            int c = _nextByte() & 0xFF;
            int code = codes[c];
            if (code == 0 && outPtr < outEnd) {
                outBuf[outPtr++] = (char) c;
                continue;
            }
            if ((len -= code) < 0) { // may need to improve error here but...
                throw _constructReadException("Malformed UTF-8 character at the end of a long (non-chunked) text segment");
            }

            switch (code) {
            case 0:
                break;
            case 1: // 2-byte UTF
                {
                    int d = _nextByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF, _inputPtr);
                    }
                    c = ((c & 0x1F) << 6) | (d & 0x3F);
                }
                break;
            case 2: // 3-byte UTF
                c = _decodeUTF8_3(c);
                break;
            case 3: // 4-byte UTF
                c = _decodeUTF8_4(c);
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outEnd) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
                outEnd = outBuf.length;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    private final void _finishChunkedText() throws JacksonException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = UTF8_UNIT_CODES;
        int outEnd = outBuf.length;
        final byte[] input = _inputBuffer;

        _chunkEnd = _inputPtr;
        _chunkLeft = 0;
        
        while (true) {
            // at byte boundary fine to get break marker, hence different:
            if (_inputPtr >= _chunkEnd) {
                // end of chunk? get a new one, if there is one; if not, we are done
                if (_chunkLeft == 0) {
                    int len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_TEXT);
                    if (len <= 0) { // fine at this point (but not later)
                        // 01-Apr-2021 (sic!), tatu: 0-byte length legal if nonsensical
                        if (len == 0) {
                            continue;
                        }
                        break;
                    }
                    _chunkLeft = len;
                    int end = _inputPtr + len;
                    if (end <= _inputEnd) { // all within buffer
                        _chunkLeft = 0;
                        _chunkEnd = end;
                    } else { // stretches beyond
                        _chunkLeft = (end - _inputEnd);
                        _chunkEnd = _inputEnd;
                    }
                }
                // besides of which just need to ensure there's content
                if (_inputPtr >= _inputEnd) { // end of buffer, but not necessarily chunk
                    loadMoreGuaranteed();
                    int end = _inputPtr + _chunkLeft;
                    if (end <= _inputEnd) { // all within buffer
                        _chunkLeft = 0;
                        _chunkEnd = end;
                    } else { // stretches beyond
                        _chunkLeft = (end - _inputEnd);
                        _chunkEnd = _inputEnd;
                    }
                }
            }
            int c = input[_inputPtr++] & 0xFF;
            int code = codes[c];
            if (code == 0 && outPtr < outEnd) {
                outBuf[outPtr++] = (char) c;
                continue;
            }

            switch (code) {
            case 0:
                break;
            case 1: // 2-byte UTF
                {
                    int d = _nextChunkedByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF, _inputPtr);
                    }
                    c = ((c & 0x1F) << 6) | (d & 0x3F);
                }
                break;
            case 2: // 3-byte UTF
                c = _decodeChunkedUTF8_3(c);
                break;
            case 3: // 4-byte UTF
                c = _decodeChunkedUTF8_4(c);
                // Let's add first part right away:
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outEnd) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
                outEnd = outBuf.length;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    private final int _nextByte() throws JacksonException {
        int inPtr = _inputPtr;
        if (inPtr < _inputEnd) {
            int ch = _inputBuffer[inPtr];
            _inputPtr = inPtr+1;
            return ch;
        }
        loadMoreGuaranteed();
        return _inputBuffer[_inputPtr++];
    }

    // NOTE! ALWAYS called for non-first byte of multi-byte UTF-8 code point
    private final int _nextChunkedByte() throws JacksonException {
        int inPtr = _inputPtr;
        
        // NOTE: _chunkEnd less than or equal to _inputEnd
        if (inPtr >= _chunkEnd) {
            return _nextChunkedByte2();
        }
        int ch = _inputBuffer[inPtr];
        _inputPtr = inPtr+1;
        return ch;
    }

    // NOTE! ALWAYS called for non-first byte of multi-byte UTF-8 code point
    private final int _nextChunkedByte2() throws JacksonException
    {
        // two possibilities: either end of buffer (in which case, just load more),
        // or end of chunk

        if (_inputPtr >= _inputEnd) { // end of buffer, but not necessarily chunk
            loadMoreGuaranteed();
            if (_chunkLeft > 0) {
                int end = _inputPtr + _chunkLeft;
                if (end <= _inputEnd) { // all within buffer
                    _chunkLeft = 0;
                    _chunkEnd = end;
                } else { // stretches beyond
                    _chunkLeft = (end - _inputEnd);
                    _chunkEnd = _inputEnd;
                }
                // either way, got it now
                return _inputBuffer[_inputPtr++];
            }
        }
        int len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_TEXT);
        // not actually acceptable if we got a split character
        // 29-Jun-2021, tatu: As per CBOR spec:
        // "Note that this implies that the bytes of a single UTF-8 character cannot be
        //  spread between chunks: a new chunk can only be started at a character boundary."
        // -> 0-length chunk not allowed either
        if (len <= 0) {
            _reportInvalidEOF(": chunked Text ends with partial UTF-8 character",
                    JsonToken.VALUE_STRING);
        }
        if (_inputPtr >= _inputEnd) { // Must have at least one byte to return
            loadMoreGuaranteed();
        }

        int end = _inputPtr + len;
        if (end <= _inputEnd) { // all within buffer
            _chunkLeft = 0;
            _chunkEnd = end;
        } else { // stretches beyond
            _chunkLeft = (end - _inputEnd);
            _chunkEnd = _inputEnd;
        }
        // either way, got it now
        return _inputBuffer[_inputPtr++];
    }

    /**
     * Helper called to complete reading of binary data ("byte string") in
     * case contents are needed.
     */
    @SuppressWarnings("resource")
    protected byte[] _finishBytes(int len) throws JacksonException
    {
        // Chunked?
        // First, simple: non-chunked
        if (len <= 0) {
            if (len == 0) {
                return NO_BYTES;
            }
            return _finishChunkedBytes();
        }
        // Non-chunked, contiguous
        if (len > LONGEST_NON_CHUNKED_BINARY) {
            // [dataformats-binary#186]: avoid immediate allocation for longest
            return _finishLongContiguousBytes(len);
        }

        final byte[] b = new byte[len];
        final int expLen = len;
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _reportIncompleteBinaryRead(expLen, 0);
            }
        }

        int ptr = 0;
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            System.arraycopy(_inputBuffer, _inputPtr, b, ptr, toAdd);
            _inputPtr += toAdd;
            ptr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                return b;
            }
            if (!loadMore()) {
                _reportIncompleteBinaryRead(expLen, ptr);
            }
        }
    }

    // @since 2.12
    protected byte[] _finishChunkedBytes() throws JacksonException
    {
        // or, if not, chunked...
        ByteArrayBuilder bb = _getByteArrayBuilder();
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            if (ch == 0xFF) { // end marker
                break;
            }
            // verify that type matches
            int type = (ch >> 5);
            if (type != CBORConstants.MAJOR_TYPE_BYTES) {
                throw _constructReadException(
"Mismatched chunk in chunked content: expected %d but encountered %d",
CBORConstants.MAJOR_TYPE_BYTES, type);
            }
            int len = _decodeExplicitLength(ch & 0x1F);
            if (len < 0) {
                throw _constructReadException("Illegal chunked-length indicator within chunked-length value (type %d)",
                        CBORConstants.MAJOR_TYPE_BYTES);
            }
            final int chunkLen = len;
            while (len > 0) {
                int avail = _inputEnd - _inputPtr;
                if (_inputPtr >= _inputEnd) {
                    if (!loadMore()) {
                        _reportIncompleteBinaryRead(chunkLen, chunkLen-len);
                    }
                    avail = _inputEnd - _inputPtr;
                }
                int count = Math.min(avail, len);
                bb.write(_inputBuffer, _inputPtr, count);
                _inputPtr += count;
                len -= count;
            }
        }
        return bb.toByteArray();
    }

    // @since 2.12
    protected byte[] _finishLongContiguousBytes(final int expLen) throws JacksonException
    {
        int left = expLen;

        // 04-Dec-2020, tatu: Let's NOT use recycled instance since we have much
        //   longer content and there is likely less benefit of trying to recycle
        //   segments
        try (final ByteArrayBuilder bb = new ByteArrayBuilder(LONGEST_NON_CHUNKED_BINARY >> 1)) {
            while (left > 0) {
                int avail = _inputEnd - _inputPtr;
                if (avail <= 0) {
                    if (!loadMore()) {
                        _reportIncompleteBinaryRead(expLen, expLen-left);
                    }
                    avail = _inputEnd - _inputPtr;
                }
                int count = Math.min(avail, left);
                bb.write(_inputBuffer, _inputPtr, count);
                _inputPtr += count;
                left -= count;
            }
            return bb.toByteArray();
        }
    }

    protected final JsonToken _decodePropertyName() throws JacksonException
    {     
        if (_inputPtr >= _inputEnd) {
            // 30-Jan-2021, tatu: To get more specific exception, won't use
            //   "loadMoreGuaranteed()" but instead:
            if (!loadMore()) {
                _eofAsNextToken();
            }
        }
        final int ch = _inputBuffer[_inputPtr++];
        final int type = ((ch >> 5) & 0x7);

        // Expecting a String, but may need to allow other types too
        if (type != CBORConstants.MAJOR_TYPE_TEXT) { // the usual case
            if (ch == -1) {
                if (!_streamReadContext.hasExpectedLength()) {
                    _streamReadContext = _streamReadContext.getParent();
                    return JsonToken.END_OBJECT;
                }
                _reportUnexpectedBreak();
            }
            // offline non-String cases, as they are expected to be rare
            _decodeNonStringName(ch);
            return JsonToken.PROPERTY_NAME;
        }
        final int lenMarker = ch & 0x1F;
        String name;
        if (lenMarker <= 23) {
            if (lenMarker == 0) {
                name = "";
            } else {
                if ((_inputEnd - _inputPtr) < lenMarker) {
                    _loadToHaveAtLeast(lenMarker);
                }
                if (_symbolsCanonical) {
                    name = _findDecodedFromSymbols(lenMarker);
                    if (name != null) {
                        _inputPtr += lenMarker;
                    } else {
                        name = _decodeContiguousName(lenMarker);
                        name = _addDecodedToSymbols(lenMarker, name);
                    }
                } else {
                    name = _decodeContiguousName(lenMarker);
                }
            }
        } else {
            final int actualLen = _decodeExplicitLength(lenMarker);
            if (actualLen < 0) {
                name = _decodeChunkedName();
            } else {
                name = _decodeLongerName(actualLen);
            }
        }
        _streamReadContext.setCurrentName(name);
        return JsonToken.PROPERTY_NAME;
    }

    private final String _decodeContiguousName(final int len) throws JacksonException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = UTF8_UNIT_CODES;
        final byte[] inBuf = _inputBuffer;

        // First a tight loop for ASCII
        final int end = inPtr + len;
        while (true) {
            int i = inBuf[inPtr] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                break;
            }
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                return _textBuffer.setCurrentAndReturn(outPtr);
            }
        }

        // But in case there's multi-byte char, use a full loop
        while (inPtr < end) {
            int i = inBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // 05-Jul-2021, tatu: As per [dataformats-binary#289] need to
                //     be careful wrt end-of-buffer truncated codepoints
                if ((inPtr + code) > end) {
                    final int firstCharOffset = len - (end - inPtr) - 1;
                    _reportTruncatedUTF8InName(len, firstCharOffset, i, code);
                }

                switch (code) {
                case 1:
                    {
                        final int c2 = inBuf[inPtr++];
                        if ((c2 & 0xC0) != 0x080) {
                            _reportInvalidOther(c2 & 0xFF, inPtr);
                        }
                        i = ((i & 0x1F) << 6) | (c2 & 0x3F);
                    }
                    break;
                case 2:
                    {
                        final int c2 = inBuf[inPtr++];
                        if ((c2 & 0xC0) != 0x080) {
                            _reportInvalidOther(c2 & 0xFF, inPtr);
                        }
                        final int c3 = inBuf[inPtr++];
                        if ((c3 & 0xC0) != 0x080) {
                            _reportInvalidOther(c3 & 0xFF, inPtr);
                        }
                        i = ((i & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                    }
                    break;
                case 3:
                    // 30-Jan-2021, tatu: TODO - validate surrogate case too?
                    i = ((i & 0x07) << 18)
                        | ((inBuf[inPtr++] & 0x3F) << 12)
                        | ((inBuf[inPtr++] & 0x3F) << 6)
                        | (inBuf[inPtr++] & 0x3F);
                    // note: this is the codepoint value; need to split, too
                    i -= 0x10000;
                    outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                    i = 0xDC00 | (i & 0x3FF);
                    break;
                default: // invalid
                    throw _constructReadException("Invalid UTF-8 byte 0x%s in Object property name",
                            Integer.toHexString(i));
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    private final String _decodeLongerName(int len) throws JacksonException
    {
        // [dataformats-binary#288]: non-canonical length of 0 needs to be
        // dealt with
        if (len == 0)  {
            return "";
        }
        // Do we have enough buffered content to read?
        if ((_inputEnd - _inputPtr) < len) {
            // or if not, could we read?
            if (len >= _inputBuffer.length) {
                // If not enough space, need handling similar to chunked
                _finishLongText(len);
                return _textBuffer.contentsAsString();
            }
            _loadToHaveAtLeast(len);
        }
        if (_symbolsCanonical) {
            String name = _findDecodedFromSymbols(len);
            if (name != null) {
                _inputPtr += len;
                return name;
            }
            name = _decodeContiguousName(len);
            return _addDecodedToSymbols(len, name);
        }
        return _decodeContiguousName(len);
    }

    private final String _decodeChunkedName() throws JacksonException
    {
        _finishChunkedText();
        return _textBuffer.contentsAsString();
    }

    /**
     * Method that handles initial token type recognition for token
     * that has to be either PROPERTY_NAME or END_OBJECT.
     */
    protected final String _decodeNonStringName(int ch) throws JacksonException
    {
        final int type = ((ch >> 5) & 0x7);
        String name;
        if (type == CBORConstants.MAJOR_TYPE_INT_POS) {
            name = _numberToName(ch, false);
        } else if (type == CBORConstants.MAJOR_TYPE_INT_NEG) {
            name = _numberToName(ch, true);
        } else if (type == CBORConstants.MAJOR_TYPE_BYTES) {
            // 08-Sep-2014, tatu: As per [Issue#5], there are codecs
            //   (f.ex. Perl module "CBOR::XS") that use Binary data...
            final int blen = _decodeExplicitLength(ch & 0x1F);
            byte[] b = _finishBytes(blen);
            // TODO: Optimize, if this becomes commonly used & bottleneck; we have
            //  more optimized UTF-8 codecs available.
            name = new String(b, UTF8);
        } else {
            if ((ch & 0xFF) == CBORConstants.INT_BREAK) {
                _reportUnexpectedBreak();
            }
            throw _constructReadException("Unsupported major type (%d) for CBOR Objects, not (yet?) supported, only Strings",
                    type);
        }
        _streamReadContext.setCurrentName(name);
        return name;
    }

    /**
     * Helper method for trying to find specified encoded UTF-8 byte sequence
     * from symbol table; if successful avoids actual decoding to String.
     *<p>
     * NOTE: caller MUST ensure input buffer has enough content.
     */
    private final String _findDecodedFromSymbols(final int len) throws JacksonException
    {
        // First: maybe we already have this name decoded?
        if (len < 5) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            int q = inBuf[inPtr] & 0xFF;
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    if (len > 3) {
                        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q;
            return _symbols.findName(q);
        }

        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;

        // First quadbyte is easy
        int q1 = (inBuf[inPtr++] & 0xFF);
        q1 = (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 = (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 = (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        
        if (len < 9) {
            int q2 = (inBuf[inPtr++] & 0xFF);
            int left = len - 5;
            if (left > 0) {
                q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            return _symbols.findName(q1, q2);
        }

        int q2 = (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);

        if (len < 13) {
            int q3 = (inBuf[inPtr++] & 0xFF);
            int left = len - 9;
            if (left > 0) {
                q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            _quad3 = q3;
            return _symbols.findName(q1, q2, q3);
        }
        return _findDecodedLong(len, q1, q2);
    }

    /**
     * Method for locating names longer than 8 bytes (in UTF-8)
     */
    private final String _findDecodedLong(int len, int q1, int q2) throws JacksonException
    {
        // first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, bufLen);
            }
        }
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;
        
        // then decode, full quads first
        int offset = 2;
        int inPtr = _inputPtr+8;
        len -= 8;
        
        final byte[] inBuf = _inputBuffer;
        do {
            int q = (inBuf[inPtr++] & 0xFF);
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            _quadBuffer[offset++] = q;
        } while ((len -= 4) > 3);
        // and then leftovers
        if (len > 0) {
            int q = inBuf[inPtr] & 0xFF;
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                }
            }
            _quadBuffer[offset++] = q;
        }
        return _symbols.findName(_quadBuffer, offset);
    }

    private final String _addDecodedToSymbols(int len, String name) {
        if (len < 5) {
            return _symbols.addName(name, _quad1);
        }
        if (len < 9) {
            return _symbols.addName(name, _quad1, _quad2);
        }
        if (len < 13) {
            return _symbols.addName(name, _quad1, _quad2, _quad3);
        }
        int qlen = (len + 3) >> 2;
        return _symbols.addName(name, _quadBuffer, qlen);
    }
    
    private static int[] _growArrayTo(int[] arr, int minSize) {
        return Arrays.copyOf(arr, minSize+4);
    }

    // Helper method needed to fix [dataformats-binary#312], masking of 0x00 character
    // 26-Feb-2022, tatu: not yet used
    /*
    private final static int _padLastQuad(int q, int bytes) {
        return (bytes == 4) ? q : (q | (-1 << (bytes << 3)));
    }
    */

    /*
    /**********************************************************************
    /* Internal methods, skipping
    /**********************************************************************
     */

    /**
     * Method called to skip remainders of an incomplete token, when
     * contents themselves will not be needed any more.
     * Only called or byte array and text.
     */
    protected void _skipIncomplete() throws JacksonException
    {
        _tokenIncomplete = false;
        final int type = ((_typeByte >> 5) & 0x7);

        // Either String or byte[]
        if (type != CBORConstants.MAJOR_TYPE_TEXT
                && type != CBORConstants.MAJOR_TYPE_BYTES) {
            _throwInternal();
        }
        final int lowBits = _typeByte & 0x1F;

        if (lowBits <= 23) {
            if (lowBits > 0) {
                _skipBytes(lowBits);
            }
            return;
        }
        switch (lowBits) {
        case 24:
            _skipBytes(_decode8Bits());
            break;
        case 25:
            _skipBytes(_decode16Bits());
            break;
        case 26:
            _skipBytes(_decode32Bits());
            break;
        case 27: // seriously?
            _skipBytesL(_decode64Bits());
            break;
        case 31:
            _skipChunked(type);
            break;
        default:
            _invalidToken(_typeByte);
        }
    }
    
    protected void _skipChunked(int expectedType) throws JacksonException
    {
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            if (ch == 0xFF) {
                return;
            }
            // verify that type matches
            int type = (ch >> 5);
            if (type != expectedType) {
                throw _constructReadException("Mismatched chunk in chunked content: expected "+expectedType
                        +" but encountered "+type);
            }

            final int lowBits = ch & 0x1F;

            if (lowBits <= 23) {
                if (lowBits > 0) {
                    _skipBytes(lowBits);
                }
                continue;
            }
            switch (lowBits) {
            case 24:
                _skipBytes(_decode8Bits());
                break;
            case 25:
                _skipBytes(_decode16Bits());
                break;
            case 26:
                _skipBytes(_decode32Bits());
                break;
            case 27: // seriously?
                _skipBytesL(_decode64Bits());
                break;
            case 31:
                throw _constructReadException(
"Illegal chunked-length indicator within chunked-length value (type %d)",
expectedType);
            default:
                _invalidToken(_typeByte);
            }
        }
    }
    
    protected void _skipBytesL(long llen) throws JacksonException
    {
        while (llen > MAX_INT_L) {
            _skipBytes((int) MAX_INT_L);
            llen -= MAX_INT_L;
        }
        _skipBytes((int) llen);
    }

    protected void _skipBytes(int len) throws JacksonException
    {
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            _inputPtr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                return;
            }
            loadMoreGuaranteed();
        }
    }

    /*
    /**********************************************************************
    /* Internal methods, length/number decoding
    /**********************************************************************
     */

    private final int _decodeTag(int lowBits) throws JacksonException
    {
        if (lowBits <= 23) {
            return lowBits;
        }
        switch (lowBits - 24) {
        case 0:
            return _decode8Bits();
        case 1:
            return _decode16Bits();
        case 2:
            return _decode32Bits();
        case 3:
            // 16-Jan-2014, tatu: Technically legal, but nothing defined, so let's
            //   only allow for cases where encoder is being wasteful...
            long l = _decode64Bits();
            if (l < MIN_INT_L || l > MAX_INT_L) {
                throw _constructReadException("Illegal Tag value: %d", l);
            }
            return (int) l;
        }
        throw _constructReadException("Invalid low bits for Tag token: 0x%s",
                Integer.toHexString(lowBits));
    }

    /**
     * Method used to decode explicit length of a variable-length value
     * (or, for indefinite/chunked, indicate that one is not known).
     * Note that long (64-bit) length is only allowed if it fits in
     * 32-bit signed int, for now; expectation being that longer values
     * are always encoded as chunks.
     */
    private final int _decodeExplicitLength(int lowBits) throws JacksonException
    {
        // common case, indefinite length; relies on marker
        if (lowBits == 31) {
            return -1;
        }
        if (lowBits <= 23) {
            return lowBits;
        }
        switch (lowBits - 24) {
        case 0:
            return _decode8Bits();
        case 1:
            return _decode16Bits();
        case 2:
            return _decode32Bits();
        case 3:
            long l = _decode64Bits();
            if (l < 0 || l > MAX_INT_L) {
                throw _constructReadException("Illegal length for "+currentToken()+": "+l);
            }
            return (int) l;
        }
        throw _constructReadException("Invalid length for %s: 0x%02X,",
                currentToken(), lowBits);
    }

    private int _decodeChunkLength(int expType) throws JacksonException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
        if (ch == CBORConstants.INT_BREAK) {
            return -1;
        }
        int type = (ch >> 5);
        if (type != expType) {
            throw _constructReadException(String.format(
"Mismatched chunk in chunked content: expected major type %d but encountered %d (byte 0x%02X)",
expType, type, ch));
        }
        int len = _decodeExplicitLength(ch & 0x1F);
        if (len < 0) {
            throw _constructReadException(
"Illegal chunked-length indicator within chunked-length value (major type %d)", expType);
        }
        return len;
    }

    private float _decodeHalfSizeFloat() throws JacksonException
    {
        int i16 = _decode16Bits() & 0xFFFF;

        boolean neg = (i16 >> 15) != 0;
        int e = (i16 >> 10) & 0x1F;
        int f = i16 & 0x03FF;

        if (e == 0) {
            float result = (float) (MATH_POW_2_NEG14 * (f / MATH_POW_2_10));
            return neg ? -result : result;
        }
        if (e == 0x1F) {
            if (f != 0) return Float.NaN;
            return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        float result = (float) (Math.pow(2, e - 15) * (1 + f / MATH_POW_2_10));
        return neg ? -result : result;
    }

    private final int _decode8Bits() throws JacksonException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return _inputBuffer[_inputPtr++] & 0xFF;
    }
    
    private final int _decode16Bits() throws JacksonException {
        int ptr = _inputPtr;
        if ((ptr + 1) >= _inputEnd) {
            return _slow16();
        }
        final byte[] b = _inputBuffer;
        int v = ((b[ptr] & 0xFF) << 8) + (b[ptr+1] & 0xFF);
        _inputPtr = ptr+2;
        return v;
    }

    private final int _slow16() throws JacksonException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
    }
    
    private final int _decode32Bits() throws JacksonException {
        int ptr = _inputPtr;
        if ((ptr + 3) >= _inputEnd) {
            return _slow32();
        }
        final byte[] b = _inputBuffer;
        int v = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        _inputPtr = ptr;
        return v;
    }

    private final int _slow32() throws JacksonException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = _inputBuffer[_inputPtr++]; // sign will disappear anyway
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v = (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v = (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
    }
    
    private final long _decode64Bits() throws JacksonException {
        int ptr = _inputPtr;
        if ((ptr + 7) >= _inputEnd) {
            return _slow64();
        }
        final byte[] b = _inputBuffer;
        int i1 = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        int i2 = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        _inputPtr = ptr;
        return _long(i1, i2);
    }

    private final long _slow64() throws JacksonException {
        return _long(_decode32Bits(), _decode32Bits());
    }
    
    private final static long _long(int i1, int i2)
    {
        long l1 = i1;
        long l2 = i2;
        l2 = (l2 << 32) >>> 32;
        return (l1 << 32) + l2;
    }

    /**
     * Helper method to encapsulate details of handling of mysterious `undefined` value
     * that is allowed to be used as something encoder could not handle (as per spec),
     * whatever the heck that should be.
     * Current definition for 2.9 is that we will be return {@link JsonToken#VALUE_NULL}, but
     * for later versions it is likely that we will alternatively allow decoding as
     * {@link JsonToken#VALUE_EMBEDDED_OBJECT} with "embedded value" of `null`.
     */
    protected JsonToken _decodeUndefinedValue() throws JacksonException {
        return JsonToken.VALUE_NULL;
    }

    /**
     * Helper method that deals with details of decoding unallocated "simple values"
     * and exposing them as expected token.
     *<p>
     * As of Jackson 2.12, simple values are exposed as
     * {@link JsonToken#VALUE_NUMBER_INT}s,
     * but in later versions this is planned to be changed to separate value type.
     *
     * @since 2.12
     */
    public JsonToken _decodeSimpleValue(int lowBits, int ch) throws JacksonException {
        if (lowBits > 24) {
            _invalidToken(ch);
        }
        if (lowBits < 24) {
            _numberInt = lowBits;
        } else { // need another byte
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            _numberInt = _inputBuffer[_inputPtr++] & 0xFF;
            // As per CBOR spec, values below 32 not allowed to avoid
            // confusion (as well as guarantee uniqueness of encoding)
            if (_numberInt < 32) {
                throw _constructReadException("Invalid second byte for simple value: 0x"
                        +Integer.toHexString(_numberInt)+" (only values 0x20 - 0xFF allowed)");
            }
        }

        // 25-Nov-2020, tatu: Although ideally we should report these
        //    as `JsonToken.VALUE_EMBEDDED_OBJECT`, due to late addition
        //    of handling in 2.12, simple value in 2.12 will be reported
        //    as simple ints.

        _numTypesValid = NR_INT;
        return (JsonToken.VALUE_NUMBER_INT);
    }

    /*
    /**********************************************************************
    /* Internal methods, UTF8 decoding
    /**********************************************************************
     */

    /*
    private final int X_decodeUTF8_2(int c) throws JacksonException {
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }
    */

    private final int _decodeUTF8_3(int c1) throws JacksonException
    {
        c1 &= 0x0F;
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private final int _decodeChunkedUTF8_3(int c1) throws JacksonException
    {
        c1 &= 0x0F;
        int d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }
    
    /**
     * @return Character value <b>minus 0x10000</c>; this so that caller
     *    can readily expand it to actual surrogates
     */
    private final int _decodeUTF8_4(int c) throws JacksonException
    {
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    private final int _decodeChunkedUTF8_4(int c) throws JacksonException
    {
        int d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    /*
    /**********************************************************************
    /* Low-level reading, other
    /**********************************************************************
     */

    protected boolean loadMore() throws JacksonException
    {
        if (_inputStream != null) {
            _currInputProcessed += _inputEnd;

            final int toRead = _inputBuffer.length;
            int count;
            try {
                count = _inputStream.read(_inputBuffer, 0, toRead);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            if (count > 0) {
                _inputPtr = 0;
                _inputEnd = count;
                return true;
            }
            // End of input
            _closeInput();
            // Should never return 0, so let's fail
            if (count == 0) {
                _reportBadInputStream(toRead);
            }
        }
        return false;
    }

    protected void loadMoreGuaranteed() throws JacksonException {
        if (!loadMore()) { _reportInvalidEOF(); }
    }

    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected final void _loadToHaveAtLeast(int minAvailable) throws JacksonException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            throw _constructReadException("Needed to read "+minAvailable+" bytes, reached end-of-input");
        }
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            //_currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        // Needs to be done here, as per [dataformats-binary#178]
        _currInputProcessed += _inputPtr;
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count;
            final int toRead = _inputBuffer.length - _inputEnd;
            try {
                count = _inputStream.read(_inputBuffer, _inputEnd, toRead);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            if (count < 1) {
                // End of input
                _closeInput();
                // Should never return 0, so let's fail
                if (count == 0) {
                    _reportBadInputStream(toRead);
                }
                throw _constructReadException("Needed to read "+minAvailable+" bytes, missed "+minAvailable+" before end-of-input");
            }
            _inputEnd += count;
        }
    }

    // @since 2.12.2
    protected final boolean _tryToLoadToHaveAtLeast(int minAvailable) throws JacksonException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            return false;
        }
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            //_currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        // Needs to be done here, as per [dataformats-binary#178]
        _currInputProcessed += _inputPtr;
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count;
            try {
                count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            if (count < 1) {
                // End of input; not ideal but we'll accept it here
                _closeInput();
                return false;
            }
            _inputEnd += count;
        }
        return true;
    }

    @Override
    protected void _closeInput() {
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE)) {
                try {
                    _inputStream.close();
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
            _inputStream = null;
        }
    }

    @Override
    protected void _handleEOF() throws StreamReadException {
        if (_streamReadContext.inRoot()) {
            return;
        }
        // Ok; end-marker or fixed-length Array/Object?
        final JsonLocation loc = _streamReadContext.startLocation(_ioContext.contentReference());
        final String startLocDesc = (loc == null) ? "[N/A]" : loc.sourceDescription();
        if (_streamReadContext.hasExpectedLength()) { // specific length
            final int expMore = _streamReadContext.getRemainingExpectedLength();
            if (_streamReadContext.inArray()) {
                _reportInvalidEOF(String.format(
                        " in Array value: expected %d more elements (start token at %s)",
                        expMore, startLocDesc),
                        null);
            } else {
                _reportInvalidEOF(String.format(
                        " in Object value: expected %d more properties (start token at %s)",
                        expMore, startLocDesc),
                        null);
            }
        } else {
            if (_streamReadContext.inArray()) {
                _reportInvalidEOF(String.format(
                        " in Array value: expected an element or close marker (0xFF) (start token at %s)",
                        startLocDesc),
                        null);
            } else {
                _reportInvalidEOF(String.format(
                        " in Object value: expected a property or close marker (0xFF) (start token at %s)",
                        startLocDesc),
                        null);
            }
        }
    }

    /*
    /**********************************************************************
    /* Internal methods, error handling, reporting
    /**********************************************************************
     */

    protected JsonToken _eofAsNextToken() throws JacksonException {
        // NOTE: here we can and should close input, release buffers, since
        // this is "hard" EOF, not a boundary imposed by header token.
        _tagValue = -1;
        close();
        // 30-Jan-2021, tatu: But also MUST verify that end-of-content is actually
        //   allowed (see [dataformats-binary#240] for example)
        _handleEOF();
        return (_currToken = null);
    }

    /*
    /**********************************************************
    /* Internal methods, error handling, reporting
    /**********************************************************
     */

    protected void _invalidToken(int ch) throws StreamReadException {
        ch &= 0xFF;
        if (ch == 0xFF) {
            throw _constructReadException("Mismatched BREAK byte (0xFF): encountered where value expected");
        }
        throw _constructReadException("Invalid CBOR value token (first byte): 0x"+Integer.toHexString(ch));
    }

    protected void _reportUnexpectedBreak() throws StreamReadException {
        if (_streamReadContext.inRoot()) {
            throw _constructReadException("Unexpected Break (0xFF) token in Root context");
        }
        throw _constructReadException("Unexpected Break (0xFF) token in definite length ("
                +_streamReadContext.getExpectedLength()+") "
                +(_streamReadContext.inObject() ? "Object" : "Array" ));
    }

    protected void _reportInvalidChar(int c) throws StreamReadException {
        // Either invalid WS or illegal UTF-8 start char
        if (c < ' ') {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }

    protected void _reportInvalidInitial(int mask) throws StreamReadException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask) throws StreamReadException {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask, int ptr) throws StreamReadException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    protected void _reportIncompleteBinaryRead(int expLen, int actLen) throws StreamReadException
    {
        _reportInvalidEOF(String.format(" for Binary value: expected %d bytes, only found %d",
                expLen, actLen), _currToken);
    }

    // @since 2.13
    /*
    private String _reportTruncatedUTF8InString(int strLenBytes, int truncatedCharOffset,
            int firstUTFByteValue, int bytesExpected)
        throws JacksonException
    {
        throw _constructReadException(String.format(
"Truncated UTF-8 character in Chunked Unicode String value (%d bytes): "
+"byte 0x%02X at offset #%d indicated %d more bytes needed",
strLenBytes, firstUTFByteValue, truncatedCharOffset, bytesExpected));
    }
    */

    // @since 2.13
    private String _reportTruncatedUTF8InName(int strLenBytes, int truncatedCharOffset,
            int firstUTFByteValue, int bytesExpected)
        throws JacksonException
    {
        throw _constructReadException(String.format(
"Truncated UTF-8 character in Map key (%d bytes): "
+"byte 0x%02X at offset #%d indicated %d more bytes needed",
strLenBytes, firstUTFByteValue, truncatedCharOffset, bytesExpected));
    }

    /*
    /**********************************************************************
    /* Internal methods, other
    /**********************************************************************
     */

    private final static BigInteger BIT_63 = BigInteger.ONE.shiftLeft(63);

    private final BigInteger _bigPositive(long l) {
        BigInteger biggie = BigInteger.valueOf((l << 1) >>> 1);
        return biggie.or(BIT_63);
    }

    private final BigInteger _bigNegative(long l) {
        // 03-Dec-2017, tatu: [dataformats-binary#124] Careful with overflow
        BigInteger unsignedBase = _bigPositive(l);
        return unsignedBase.negate().subtract(BigInteger.ONE);
    }
}
