package com.fasterxml.jackson.dataformat.smile;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.core.util.TextBuffer;

/**
 * @since 2.9
 */
public abstract class SmileParserBase extends ParserMinimalBase
{
    protected final static String[] NO_STRINGS = new String[0];

    // 2.12.3: [dataformats-binary#260] Avoid OOME/DoS for bigger binary;
    //  read only up to 250k
    protected final static int LONGEST_NON_CHUNKED_BINARY = 250_000;

    // @since 2.16
    protected final static int DEFAULT_NAME_BUFFER_LENGTH = 64;    

    // @since 2.16
    protected final static int DEFAULT_STRING_VALUE_BUFFER_LENGTH = 64;

    // @since 2.14
    protected final static JacksonFeatureSet<StreamReadCapability> SMILE_READ_CAPABILITIES
        = DEFAULT_READ_CAPABILITIES.with(StreamReadCapability.EXACT_FLOATS);

    /*
    /**********************************************************************
    /* Config
    /**********************************************************************
     */

    /**
     * Bit flag composed of bits that indicate which
     * {@link SmileParser.Feature}s are enabled.
     *<p>
     * NOTE: currently the only feature ({@link SmileParser.Feature#REQUIRE_HEADER}
     * takes effect during bootstrapping.
     */
    protected int _formatFeatures;

    /**
     * Flag that indicates whether content can legally have raw (unquoted)
     * binary data. Since this information is included both in header and
     * in actual binary data blocks there is redundancy, and we want to
     * ensure settings are compliant. Using application may also want to
     * know this setting in case it does some direct (random) access.
     */
    protected boolean _mayContainRawBinary;

    /*
    /**********************************************************************
    /* Generic I/O state
    /**********************************************************************
     */

    /**
     * I/O context for this reader. It handles buffer allocation
     * for the reader.
     */
    protected final IOContext _ioContext;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;

    /*
    /**********************************************************************
    /* Current input data
    /**********************************************************************
     */

    // Note: type of actual buffer depends on sub-class, can't include

    /**
     * Pointer to next available character in buffer
     */
    protected int _inputPtr = 0;

    /**
     * Index of character after last available one in the buffer.
     */
    protected int _inputEnd = 0;

    /*
    /**********************************************************************
    /* Parsing state, location
    /**********************************************************************
     */

    /**
     * Number of characters/bytes that were contained in previous blocks
     * (blocks that were already processed prior to the current buffer),
     * minus offset of the input buffer at which content starts: so that
     * {@code _currInputProcessed + _inputPtr} is the absolute byte offset
     * of the current input position.
     */
    protected long _currInputProcessed;

    /**
     * Offset within input buffer of the start of the current token.
     *
     * @deprecated Since 2.23 replaced by {@link #_tokenInputTotal} (no longer
     *   used or updated)
     */
    @Deprecated
    protected int _tokenOffsetForTotal;

    /**
     * Absolute byte offset (from the beginning of the whole content) of the
     * start of the current token: absolute, as opposed to relative to current
     * input buffer, since a token may span multiple buffers.
     *
     * @since 2.23
     */
    protected long _tokenInputTotal;

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     *<p>
     * NOTE: before 2.13 was "_parsingContext"
     */
    protected JsonReadContext _streamReadContext;

    /*
    /**********************************************************************
    /* Decoded values, text, binary
    /**********************************************************************
     */

    /**
     * Buffer that contains contents of String values, including
     * field names if necessary (name split across boundary,
     * contains escape sequence, or access needed to char array)
     */
    protected final TextBuffer _textBuffer;

    /**
     * Temporary buffer that is needed if field name is accessed
     * using {@link #getTextCharacters} method (instead of String
     * returning alternatives)
     */
    protected char[] _nameCopyBuffer;

    /**
     * Flag set to indicate whether the field name is available
     * from the name copy buffer or not (in addition to its String
     * representation  being available via read context)
     */
    protected boolean _nameCopied;

    /**
     * We will hold on to decoded binary data, for duration of
     * current event, so that multiple calls to
     * {@link #getBinaryValue} will not need to decode data more
     * than once.
     */
    protected byte[] _binaryValue;

    /*
    /**********************************************************************
    /* Decoded values, numbers
    /**********************************************************************
     */

    protected NumberType _numberType;

    /**
     * Bitfield that indicates which numeric representations
     * have been calculated for the current type
     */
    protected int _numTypesValid = NR_UNKNOWN;

    protected BigInteger _numberBigInt;

    protected BigDecimal _numberBigDecimal;

    protected int _numberInt;

    protected float _numberFloat;

    protected long _numberLong;

    protected double _numberDouble;

    /*
    /**********************************************************************
    /* Symbol handling, decoding
    /**********************************************************************
     */

    /**
     * Symbol table that contains field names encountered so far
     */
    protected final ByteQuadsCanonicalizer _symbols;

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
    /* Back-references
    /**********************************************************************
     */

    /**
     * Array of recently seen field names, which may be back referenced
     * by later fields.
     * Defaults set to enable handling even if no header found.
     */
    protected String[] _seenNames = NO_STRINGS;

    protected int _seenNameCount = 0;

    /**
     * Array of recently seen field names, which may be back referenced
     * by later fields
     * Defaults set to disable handling if no header found.
     */
    protected String[] _seenStringValues = null;

    protected int _seenStringValueCount = -1;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected SmileParserBase(IOContext ctxt, int parserFeatures, int formatFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(parserFeatures, ctxt.streamReadConstraints());
        _formatFeatures = formatFeatures;
        _ioContext = ctxt;
        _symbols = sym;
        _symbolsCanonical = sym.isCanonicalizing();
        DupDetector dups = Feature.STRICT_DUPLICATE_DETECTION.enabledIn(parserFeatures)
                ? DupDetector.rootDetector(this) : null;
        _streamReadContext = JsonReadContext.createRootContext(dups);
        _textBuffer = ctxt.constructReadConstrainedTextBuffer();
    }

    /*
    /**********************************************************
    /* Versioned
    /**********************************************************
     */

    @Override
    public final Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
     */

    public final boolean mayContainRawBinary() {
        return _mayContainRawBinary;
    }

    /*
    /**********************************************************
    /* FormatFeature support
    /**********************************************************
     */

    @Override
    public final int getFormatFeatures() {
        return _formatFeatures;
    }

    @Override
    public final JsonParser overrideFormatFeatures(int values, int mask) {
        _formatFeatures = (_formatFeatures & ~mask) | (values & mask);
        return this;
    }

    @Override // since 2.12
    public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
        return SMILE_READ_CAPABILITIES;
    }

    /*
    /**********************************************************
    /* Abstract methods for sub-classes to provide
    /**********************************************************
     */

    protected abstract void _closeInput() throws IOException;

    protected abstract void _parseNumericValue() throws IOException;

    /**
     * Method called to ensure contents of current {@link JsonToken#VALUE_STRING}
     * token are fully decoded into {@link #_textBuffer}. Default implementation
     * does nothing: only needed by parsers that decode values lazily.
     *
     * @since 2.23
     */
    protected void _finishStringValue() throws IOException { }

//  public abstract int releaseBuffered(OutputStream out) throws IOException;
//  public abstract Object getInputSource();

    /*
    /**********************************************************
    /* Abstract impls
    /**********************************************************
     */

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public final JsonLocation currentLocation()
    {
        return _locationAt(_currInputProcessed + _inputPtr);
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public final JsonLocation currentTokenLocation()
    {
        return _locationAt(_tokenInputTotal);
    }

    /**
     * @since 2.23
     */
    protected JsonLocation _locationAt(long byteOffset) {
        return new JsonLocation(_ioContext.contentReference(),
                byteOffset, // bytes
                -1, -1, (int) byteOffset); // char offset, line, column
    }

    @Deprecated // since 2.17
    @Override
    public JsonLocation getCurrentLocation() { return currentLocation(); }

    @Deprecated // since 2.17
    @Override
    public JsonLocation getTokenLocation() { return currentTokenLocation(); }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override // since 2.17
    public String currentName() throws IOException
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            return _streamReadContext.getParent().getCurrentName();
        }
        return _streamReadContext.getCurrentName();
    }

    @Deprecated // since 2.17
    @Override
    public String getCurrentName() throws IOException { return currentName(); }

    @Override
    public final void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        JsonReadContext ctxt = _streamReadContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        // Unfortunate, but since we did not expose exceptions, need to wrap
        try {
            ctxt.setCurrentName(name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public final void close() throws IOException {
        if (!_closed) {
            _closed = true;
            _inputEnd = 0;
            _symbols.release();
            // 30-May-2025, tatu: was missing before 2.20
            if (JsonParser.Feature.CLEAR_CURRENT_TOKEN_ON_CLOSE.enabledIn(_features)) {
                _currToken = null;
            }
            try {
                _closeInput();
            } finally {
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
            _ioContext.close();
        }
    }

    protected final void _releaseBuffers() throws IOException {
        _textBuffer.releaseBuffers();
        char[] buf = _nameCopyBuffer;
        if (buf != null) {
            _nameCopyBuffer = null;
            _ioContext.releaseNameCopyBuffer(buf);
        }
        _releaseBuffers2();
    }

    protected abstract void _releaseBuffers2();

    /**
     * Helper method for calculating length of 7-bit encoded content, given
     * length of raw (decoded) content: 8 encoded bytes for each full 7 bytes,
     * and for last 1 - 6 bytes one more than the number of bytes.
     * Calculated as {@code long} since may exceed {@code Integer.MAX_VALUE}.
     *
     * @since 2.18.12
     */
    protected static long _encoded7BitLength(int rawLength) {
        final int leftover = rawLength % 7;
        return (rawLength / 7) * 8L + ((leftover == 0) ? 0 : leftover + 1);
    }

    @Override public final boolean isClosed() { return _closed; }
    @Override public final JsonReadContext getParsingContext() { return _streamReadContext; }

    /*
    /**********************************************************
    /* Text accessors of public API
    /**********************************************************
     */

    @Override // since 2.8
    public int getText(Writer writer) throws IOException
    {
        final JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            _finishStringValue();
            return _textBuffer.contentsToWriter(writer);
        }
        // Binary values have no textual representation: avoid decoding them
        if (t == JsonToken.VALUE_EMBEDDED_OBJECT) {
            return 0;
        }
        if (t == JsonToken.NOT_AVAILABLE) {
            _reportError("Current token not available: can not call this method");
        }
        // [dataformats-binary#835]: only String values are decoded into
        // `_textBuffer`; others (numbers, names, markers) go via `getText()`
        return super.getText(writer);
    }

    /*
    /**********************************************************
    /* Numeric accessors of public API
    /**********************************************************
     */

    @Override // since 2.9
    public final boolean isNaN() throws IOException {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                return !Double.isFinite(_numberDouble);
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                return !Float.isFinite(_numberFloat);
            }
        }
        return false;
    }

    @Override
    public final Number getNumberValue() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _parseNumericValue(); // will also check event type
        }
        switch (_numberType) {
        case INT:
            return _numberInt;
        case LONG:
            return _numberLong;
        case BIG_INTEGER:
            return _numberBigInt;
        case FLOAT:
            return _numberFloat;
        case DOUBLE:
            return _numberDouble;
        case BIG_DECIMAL:
        default:
            return _numberBigDecimal;
        }
    }

    @Override // @since 2.12 -- for (most?) binary formats exactness guaranteed anyway
    public final Number getNumberValueExact() throws IOException {
        return getNumberValue();
    }

    @Override
    public final NumberType getNumberType() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _parseNumericValue(); // will also check event type
        }
        return _numberType;
    }

    @Override // since 2.17
    public NumberTypeFP getNumberTypeFP() throws IOException {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            // Some decoding is done lazily so need to:
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if (_numberType == NumberType.BIG_DECIMAL) {
                return NumberTypeFP.BIG_DECIMAL;
            }
            if (_numberType == NumberType.DOUBLE) {
                return NumberTypeFP.DOUBLE64;
            }
            if (_numberType == NumberType.FLOAT) {
                return NumberTypeFP.FLOAT32;
            }
        }
        return NumberTypeFP.UNKNOWN;
    }

    @Override
    public final int getIntValue() throws IOException
    {
        if ((_numTypesValid & NR_INT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_INT) == 0) { // wasn't an int natively?
                convertNumberToInt(); // let's make it so, if possible
            }
        }
        return _numberInt;
    }

    @Override
    public final long getLongValue() throws IOException
    {
        if ((_numTypesValid & NR_LONG) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_LONG) == 0) {
                convertNumberToLong();
            }
        }
        return _numberLong;
    }

    @Override
    public final BigInteger getBigIntegerValue() throws IOException
    {
        if ((_numTypesValid & NR_BIGINT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_BIGINT) == 0) {
                convertNumberToBigInteger();
            }
        }
        return _numberBigInt;
    }

    @Override
    public final float getFloatValue() throws IOException
    {
        if ((_numTypesValid & NR_FLOAT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_FLOAT) == 0) {
                convertNumberToFloat();
            }
        }
        // Bounds/range checks would be tricky here, so let's not bother even trying...
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value (%s) out of range of Java float", getText());
        }
        */
        return _numberFloat;
    }

    @Override
    public final double getDoubleValue() throws IOException
    {
        if ((_numTypesValid & NR_DOUBLE) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_DOUBLE) == 0) {
                convertNumberToDouble();
            }
        }
        return _numberDouble;
    }

    @Override
    public final BigDecimal getDecimalValue() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _parseNumericValue(); // will also check event type
            }
            if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
                convertNumberToBigDecimal();
            }
        }
        return _numberBigDecimal;
    }

    /*
    /**********************************************************
    /* Numeric conversions
    /**********************************************************
     */

    protected final void convertNumberToInt() throws IOException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                reportOverflowInt(String.valueOf(_numberLong));
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt(String.valueOf(_numberBigInt));
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt(String.valueOf(_numberDouble));
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                reportOverflowInt(String.valueOf(_numberFloat));
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt(String.valueOf(_numberBigDecimal));
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }

    protected final void convertNumberToLong() throws IOException
    {
        int v = _numTypesValid;
        if ((v & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((v & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                reportOverflowLong(String.valueOf(_numberBigInt));
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((v & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong(String.valueOf(_numberDouble));
            }
            _numberLong = (long) _numberDouble;
        } else if ((v & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                reportOverflowLong(String.valueOf(_numberFloat));
            }
            _numberLong = (long) _numberFloat;
        } else if ((v & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong(String.valueOf(_numberBigDecimal));
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }

    protected final void convertNumberToBigInteger() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            _streamReadConstraints.validateBigIntegerScale(_numberBigDecimal.scale());
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

    protected final void convertNumberToFloat() throws IOException
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

    protected final void convertNumberToDouble() throws IOException
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

    protected final void convertNumberToBigDecimal() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            // 15-Dec-2023, tatu: Should NOT try to use String representation
            //    since we already have decoded into double
            _numberBigDecimal = new BigDecimal(_numberDouble);
        } else if ((_numTypesValid &  NR_FLOAT) != 0) {
            // 15-Dec-2023, tatu: Should NOT try to use String representation
            //    since we already have decoded into float
            _numberBigDecimal = new BigDecimal(_numberFloat);
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
    /**********************************************************
    /* Internal/package methods: other
    /**********************************************************
     */

    /**
     * Method called when an EOF is encountered between tokens.
     * If so, it may be a legitimate EOF, but only iff there
     * is no open non-root context.
     */
    @Override
    protected void _handleEOF() throws JsonParseException {
        if (!_streamReadContext.inRoot()) {
            String marker = _streamReadContext.inArray() ? "Array" : "Object";
            _reportInvalidEOF(String.format(
                    ": expected close marker for %s (start marker at %s)",
                    marker,
                    _streamReadContext.startLocation(_sourceReference())),
                    null);
        }
    }

    protected void _reportMismatchedEndMarker(int actCh, char expCh) throws JsonParseException {
        JsonReadContext ctxt = getParsingContext();
        _reportError(String.format(
                "Unexpected close marker '%s': expected '%c' (for %s starting at %s)",
                (char) actCh, expCh, ctxt.typeDesc(), ctxt.startLocation(_sourceReference())));
    }

    // @since 2.12.3 (moved from SmileParser in 2.18.12)
    protected String _reportTruncatedUTF8InString(int strLenBytes, int truncatedCharOffset,
            int firstUTFByteValue, int bytesExpected)
        throws IOException
    {
        throw _constructReadException(String.format(
"Truncated UTF-8 character in Short Unicode String value (%d bytes): "
+"byte 0x%02X at offset #%d indicated %d more bytes needed",
strLenBytes, firstUTFByteValue, truncatedCharOffset, bytesExpected));
    }

    // (moved from SmileParser in 2.18.12)
    protected String _reportTruncatedUTF8InName(int strLenBytes, int truncatedCharOffset,
            int firstUTFByteValue, int bytesExpected)
        throws IOException
    {
        throw _constructReadException(String.format(
"Truncated UTF-8 character in Short Unicode Name (%d bytes): "
+"byte 0x%02X at offset #%d indicated %d more bytes needed",
strLenBytes, firstUTFByteValue, truncatedCharOffset, bytesExpected));
    }

    /**
     * Helper method for decoding a 2-byte UTF-8 sequence fully contained in
     * given buffer: lead byte {@code c} has already been read, and continuation
     * byte is at {@code buf[ptr]}. Verifies continuation byte(s) as well as the
     * decoded code point (see {@link #_verifyUTF8_3} and {@link #_verifyUTF8_4}).
     *<p>
     * NOTE: does not change {@code _inputPtr}, not even on error: caller has
     * already advanced it past the whole String, and leaving it there
     * keeps parser state consistent.
     *<p>
     * NOTE: no code point check needed for 2-byte sequences, since lead bytes
     * 0xC0 and 0xC1 (overlong encodings) are marked invalid in
     * {@link SmileConstants#sUtf8UnitLengths}.
     *
     * @param inInputBuffer Whether {@code buf} is the input buffer (that is,
     *    {@code ptr} is an offset within current input): if so, error location
     *    points to the invalid byte; if not (copy buffer), it is the current
     *    input location.
     * @param isName Whether text being decoded is an Object property name
     *    (for error reporting)
     *
     * @return Decoded code point
     *
     * @since 2.23
     */
    protected final int _decodeUTF8InBuffer2(byte[] buf, int ptr, int c,
            boolean inInputBuffer, boolean isName)
        throws JsonParseException
    {
        // NOTE: separate small methods per length, with only cheap checks, so that
        // they get inlined (a generic loop was measurably slower); invalid sequences
        // are decoded again for error reporting
        final int d = buf[ptr];
        if ((d & 0xC0) != 0x80) {
            _reportInvalidUTF8InBuffer(buf, ptr, c, 1, inInputBuffer, isName);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    /**
     * Same as {@link #_decodeUTF8InBuffer2} but for 3-byte sequences.
     *
     * @since 2.23
     */
    protected final int _decodeUTF8InBuffer3(byte[] buf, int ptr, int c,
            boolean inInputBuffer, boolean isName)
        throws JsonParseException
    {
        final int d = buf[ptr];
        final int e = buf[ptr+1];
        final int ch = ((c & 0x0F) << 12) | ((d & 0x3F) << 6) | (e & 0x3F);
        // continuation bytes must be 10xxxxxx; no overlong forms or surrogates
        if ((((d & e) & 0xC0) != 0x80) || (((d | e) & 0x40) != 0)
                || (ch < 0x800) || ((ch & 0xF800) == 0xD800)) {
            _reportInvalidUTF8InBuffer(buf, ptr, c, 2, inInputBuffer, isName);
        }
        return ch;
    }

    /**
     * Same as {@link #_decodeUTF8InBuffer2} but for 4-byte sequences.
     *
     * @return Decoded code point (not split into surrogate pair)
     *
     * @since 2.23
     */
    protected final int _decodeUTF8InBuffer4(byte[] buf, int ptr, int c,
            boolean inInputBuffer, boolean isName)
        throws JsonParseException
    {
        final int d = buf[ptr];
        final int e = buf[ptr+1];
        final int f = buf[ptr+2];
        final int ch = ((c & 0x07) << 18) | ((d & 0x3F) << 12) | ((e & 0x3F) << 6) | (f & 0x3F);
        // continuation bytes must be 10xxxxxx; no overlong forms, max U+10FFFF
        if ((((d & e & f) & 0xC0) != 0x80) || (((d | e | f) & 0x40) != 0)
                || (ch < 0x10000) || (ch > Character.MAX_CODE_POINT)) {
            _reportInvalidUTF8InBuffer(buf, ptr, c, 3, inInputBuffer, isName);
        }
        return ch;
    }

    // Called when one of _decodeUTF8InBufferN found a problem: decodes the
    // sequence again to find and report the first problem
    private void _reportInvalidUTF8InBuffer(byte[] buf, int ptr, int c, int needed,
            boolean inInputBuffer, boolean isName)
        throws JsonParseException
    {
        // Mask lead byte: 0x1F for 2-byte, 0x0F for 3-byte, 0x07 for 4-byte
        c &= (0x3F >> needed);
        for (int i = 0; i < needed; ++i) {
            final int d = buf[ptr++];
            if ((d & 0xC0) != 0x080) {
                throw _constructUTF8InBufferException(inInputBuffer, ptr,
                        _invalidOtherMessage(d & 0xFF, isName));
            }
            c = (c << 6) | (d & 0x3F);
        }
        final String problem = _invalidUTF8Problem(c, needed+1);
        if (problem != null) {
            throw _constructUTF8InBufferException(inInputBuffer, ptr,
                    _invalidUTF8Message(problem, isName));
        }
        // Should never get here: fast and slow path checks disagree
        throw new IllegalStateException(String.format(
"Internal error: UTF-8 sequence of %d bytes reported as invalid, but no problem found (code point 0x%x)",
                needed+1, c));
    }

    private JsonParseException _constructUTF8InBufferException(boolean inInputBuffer,
            int ptr, String msg)
    {
        if (inInputBuffer) {
            return _constructReadExceptionAt(ptr, msg);
        }
        return _constructReadException(msg);
    }

    /**
     * Helper method for verifying that decoded 3-byte UTF-8 code point uses
     * the shortest form and is not a surrogate (RFC 3629).
     * Error, if any, is reported at the current input location.
     *<p>
     * NOTE: no such check is needed for 2-byte sequences, since lead bytes
     * 0xC0 and 0xC1 (overlong encodings) are marked invalid in
     * {@link SmileConstants#sUtf8UnitLengths}.
     *
     * @param ch Decoded code point
     * @param isName Whether text being decoded is an Object property name
     *    (for error reporting)
     *
     * @since 2.23
     */
    protected final void _verifyUTF8_3(int ch, boolean isName) throws JsonParseException
    {
        if ((ch < 0x800) || ((ch & 0xF800) == 0xD800)) {
            _reportInvalidUTF8(ch, 3, isName);
        }
    }

    /**
     * Helper method for verifying that decoded 4-byte UTF-8 code point uses
     * the shortest form and is at most U+10FFFF (RFC 3629).
     * Error, if any, is reported at the current input location.
     *
     * @param ch Decoded code point
     * @param isName Whether text being decoded is an Object property name
     *    (for error reporting)
     *
     * @since 2.23
     */
    protected final void _verifyUTF8_4(int ch, boolean isName) throws JsonParseException
    {
        if ((ch < 0x10000) || (ch > Character.MAX_CODE_POINT)) {
            _reportInvalidUTF8(ch, 4, isName);
        }
    }

    private void _reportInvalidUTF8(int ch, int byteCount, boolean isName)
        throws JsonParseException
    {
        _reportError(_invalidUTF8Message(_invalidUTF8Problem(ch, byteCount), isName));
    }

    // @return Description of problem with decoded code point, if any; null if valid
    private static String _invalidUTF8Problem(int ch, int byteCount)
    {
        // NOTE: 2-byte sequences can not be invalid, as lead bytes 0xC0 and 0xC1
        //   are rejected via `sUtf8UnitLengths`
        if (byteCount == 2) {
            return null;
        }
        if (byteCount == 3) {
            if (ch >= 0x800) {
                if (ch < 0xD800 || ch > 0xDFFF) {
                    return null;
                }
                return "Illegal surrogate character 0x"+Integer.toHexString(ch);
            }
            return "overlong 3-byte encoding of 0x"+Integer.toHexString(ch);
        }
        if (ch >= 0x10000) {
            if (ch <= Character.MAX_CODE_POINT) {
                return null;
            }
            return "code point 0x"+Integer.toHexString(ch)+" beyond U+10FFFF";
        }
        return "overlong 4-byte encoding of 0x"+Integer.toHexString(ch);
    }

    private static String _invalidUTF8Message(String problem, boolean isName) {
        if (isName) {
            return "Invalid UTF-8 in Object property name: "+problem;
        }
        return "Invalid UTF-8: "+problem;
    }

    /**
     * Helper method for constructing a read exception with location pointing to
     * given offset within current input buffer, without changing
     * {@code _inputPtr}.
     *
     * @param ptr Offset within current input buffer
     * @param msg Exception message
     *
     * @return Constructed exception (caller is to throw it)
     *
     * @since 2.23
     */
    protected final JsonParseException _constructReadExceptionAt(int ptr, String msg) {
        return new JsonParseException(this, msg, _locationAt(_currInputProcessed + ptr))
                .withRequestPayload(_requestPayload);
    }

    /**
     * Helper method used to encapsulate logic of including (or not) of
     * "source reference" when constructing {@link JsonLocation} instances.
     *
     * @since 2.13
     */
    protected ContentReference _sourceReference() {
        if (isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)) {
            return _ioContext.contentReference();
        }
        return ContentReference.unknown();
    }

    /**
     * Helper method needed to fix [dataformats-binary#312]: masking of 0x00
     * character. Pads unused high bytes of a partial name quad with 1s, so
     * that a shorter name cannot collide with a longer NUL-prefixed one in
     * {@code ByteQuadsCanonicalizer}.
     *
     * @since 2.21.7
     * @deprecated Since 2.23 use {@link #_padAndValidateQuad} instead
     */
    @Deprecated
    protected final static int _padQuadForNulls(int firstByte) {
        return (firstByte & 0xFF) | 0xFFFFFF00;
    }

    /**
     * Helper method for padding unused high bytes of a partial name quad with 1s
     * (see {@link #_padQuadForNulls}), given the first byte of the quad.
     *<p>
     * Since padding uses 0xFF bytes, a partial quad starting with 0xFF could match
     * a shorter name in the symbol table, skipping UTF-8 validation of the name
     * ([dataformats-binary#869]); 0xFF is never valid in UTF-8 so it is rejected.
     *
     * @since 2.23
     */
    protected final int _padAndValidateQuad(int firstByte) throws JsonParseException {
        firstByte &= 0xFF;
        if (firstByte == 0xFF) {
            _reportInvalidInitial(firstByte);
        }
        return firstByte | 0xFFFFFF00;
    }

    /**
     * Helper method for verifying that the last quad of a name, when full (not
     * padded), does not start with 0xFF: if it did, it could match the padded
     * last quad of a name one byte shorter ([dataformats-binary#869]).
     *
     * @since 2.23
     */
    protected final void _verifyLastFullQuad(int q) throws JsonParseException {
        if ((q >>> 24) == 0xFF) {
            _reportInvalidInitial(0xFF);
        }
    }

    /**
     * Helper method for reporting an invalid UTF-8 lead (first) byte at the
     * current input location.
     *
     * @param mask Value of the invalid byte (0x00 - 0xFF)
     *
     * @since 2.23 (moved from {@code SmileParser} and {@code NonBlockingParserBase})
     */
    protected void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }

    /**
     * Helper method for reporting an invalid UTF-8 continuation (middle) byte at
     * the current input location.
     *
     * @param mask Value of the invalid byte (0x00 - 0xFF)
     *
     * @since 2.23 (moved from {@code SmileParser})
     */
    protected void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError(_invalidOtherMessage(mask, false));
    }

    /**
     * Same as {@link #_reportInvalidOther(int)} but for Object property names.
     *
     * @param mask Value of the invalid byte (0x00 - 0xFF)
     *
     * @since 2.23
     */
    protected void _reportInvalidOtherInName(int mask) throws JsonParseException {
        _reportError(_invalidOtherMessage(mask, true));
    }

    /**
     * Helper method for reporting an invalid UTF-8 continuation (middle) byte,
     * after first setting {@code _inputPtr} to given offset.
     *
     * @param mask Value of the invalid byte (0x00 - 0xFF)
     * @param ptr Offset within current input buffer to report error at
     *
     * @since 2.23 (moved from {@code SmileParser} and {@code NonBlockingParserBase})
     */
    protected void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    private static String _invalidOtherMessage(int mask, boolean isName) {
        if (isName) {
            return "Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask)
                +" in Object property name";
        }
        return "Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask);
    }
}
