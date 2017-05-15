package com.fasterxml.jackson.dataformat.smile;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.TextBuffer;

/**
 * @since 2.9
 */
public abstract class SmileParserBase extends ParserMinimalBase
{
    protected final static String[] NO_STRINGS = new String[0];

    /*
    /**********************************************************
    /* Config
    /**********************************************************
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
    /**********************************************************
    /* Generic I/O state
    /**********************************************************
     */

    /**
     * I/O context for this reader. It handles buffer allocation
     * for the reader.
     */
    final protected IOContext _ioContext;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;

    /*
    /**********************************************************
    /* Current input data
    /**********************************************************
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
    /**********************************************************
    /* Parsing state, location
    /**********************************************************
     */

    /**
     * Number of characters/bytes that were contained in previous blocks
     * (blocks that were already processed prior to the current buffer).
     */
    protected long _currInputProcessed;

    /**
     * Alternative to {@link #_tokenInputTotal} that will only contain
     * offset within input buffer, as int.
     */
    protected int _tokenOffsetForTotal;

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected JsonReadContext _parsingContext;
    
    /*
    /**********************************************************
    /* Decoded values, text, binary
    /**********************************************************
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
    /**********************************************************
    /* Decoded values, numbers
    /**********************************************************
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
    /**********************************************************
    /* Symbol handling, decoding
    /**********************************************************
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
    /**********************************************************
    /* Thread-local recycling
    /**********************************************************
     */
    
    /**
     * <code>ThreadLocal</code> contains a {@link java.lang.ref.SoftReference}
     * to a buffer recycler used to provide a low-cost
     * buffer recycling for Smile-specific buffers.
     */
    final protected static ThreadLocal<SoftReference<SmileBufferRecycler<String>>> _smileRecyclerRef
        = new ThreadLocal<SoftReference<SmileBufferRecycler<String>>>();

    /**
     * Helper object used for low-level recycling of Smile-generator
     * specific buffers.
     */
    final protected SmileBufferRecycler<String> _smileBufferRecycler;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public SmileParserBase(IOContext ctxt, int parserFeatures, int formatFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(parserFeatures);
        _formatFeatures = formatFeatures;
        _ioContext = ctxt;
        _symbols = sym;
        DupDetector dups = Feature.STRICT_DUPLICATE_DETECTION.enabledIn(parserFeatures)
                ? DupDetector.rootDetector(this) : null;
        _parsingContext = JsonReadContext.createRootContext(dups);

        _textBuffer = ctxt.constructTextBuffer();
        _smileBufferRecycler = _smileBufferRecycler();
    }

    protected final static SmileBufferRecycler<String> _smileBufferRecycler()
    {
        SoftReference<SmileBufferRecycler<String>> ref = _smileRecyclerRef.get();
        SmileBufferRecycler<String> br = (ref == null) ? null : ref.get();

        if (br == null) {
            br = new SmileBufferRecycler<String>();
            _smileRecyclerRef.set(new SoftReference<SmileBufferRecycler<String>>(br));
        }
        return br;
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

    /*
    /**********************************************************
    /* Abstract methods for sub-classes to provide
    /**********************************************************
     */

    protected abstract void _closeInput() throws IOException;

    protected abstract void _parseNumericValue() throws IOException;

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
    public final JsonLocation getTokenLocation()
    {
        // token location is correctly managed...
        long total = _currInputProcessed + _tokenOffsetForTotal;
        // 2.4: used to be: _tokenInputTotal
        return new JsonLocation(_ioContext.getSourceReference(),
                total, // bytes
                -1, -1, (int) total); // char offset, line, column
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public final JsonLocation getCurrentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new JsonLocation(_ioContext.getSourceReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public final String getCurrentName() throws IOException
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            return _parsingContext.getParent().getCurrentName();
        }
        return _parsingContext.getCurrentName();
    }

    @Override
    public final void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        JsonReadContext ctxt = _parsingContext;
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
            try {
                _closeInput();
            } finally {
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
        }
    }

    protected final void _releaseBuffers() throws IOException {
        _textBuffer.releaseBuffers();
        char[] buf = _nameCopyBuffer;
        if (buf != null) {
            _nameCopyBuffer = null;
            _ioContext.releaseNameCopyBuffer(buf);
        }
        String[] nameBuf = _seenNames;
        if (nameBuf != null && nameBuf.length > 0) {
            _seenNames = null;
            // 28-Jun-2011, tatu: With 1.9, caller needs to clear the buffer;
            //   but we only need to clear up to count as it is not a hash area
            if (_seenNameCount > 0) {
                Arrays.fill(nameBuf, 0, _seenNameCount, null);
            }
            _smileBufferRecycler.releaseSeenNamesBuffer(nameBuf);
        }
        String[] valueBuf = _seenStringValues;
        if (valueBuf != null && valueBuf.length > 0) {
            _seenStringValues = null;
            // 28-Jun-2011, tatu: With 1.9, caller needs to clear the buffer;
            //   but we only need to clear up to count as it is not a hash area
            if (_seenStringValueCount > 0) {
                Arrays.fill(valueBuf, 0, _seenStringValueCount, null);
            }
            _smileBufferRecycler.releaseSeenStringValuesBuffer(valueBuf);
        }
        _releaseBuffers2();
    }

    protected abstract void _releaseBuffers2();
    
    @Override public final boolean isClosed() { return _closed; }
    @Override public final JsonReadContext getParsingContext() { return _parsingContext; }

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
            if (_numberType == NumberType.DOUBLE) {
                // 10-Mar-2017, tatu: Alas, `Double.isFinite(d)` only added in JDK 8
                double d = _numberDouble;
                return Double.isNaN(d) || Double.isInfinite(d);
            }
            if (_numberType == NumberType.FLOAT) {
                float f = _numberFloat;
                return Float.isNaN(f) || Float.isInfinite(f);
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

    @Override
    public final NumberType getNumberType() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _parseNumericValue(); // will also check event type
        }
        return _numberType;
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
                _reportError("Numeric value (%s) out of range of int", getText());
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0 
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt();
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
                reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((v & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((v & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                reportOverflowInt();
            }
            _numberLong = (long) _numberFloat;
        } else if ((v & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong();
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
        if (!_parsingContext.inRoot()) {
            String marker = _parsingContext.inArray() ? "Array" : "Object";
            _reportInvalidEOF(String.format(
                    ": expected close marker for %s (start marker at %s)",
                    marker,
                    _parsingContext.getStartLocation(_getSourceReference())),
                    null);
        }
    }

    protected void _reportMismatchedEndMarker(int actCh, char expCh) throws JsonParseException {
        JsonReadContext ctxt = getParsingContext();
        _reportError(String.format(
                "Unexpected close marker '%s': expected '%c' (for %s starting at %s)",
                (char) actCh, expCh, ctxt.typeDesc(), ctxt.getStartLocation(_getSourceReference())));
    }

    /**
     * Helper method used to encapsulate logic of including (or not) of
     * "source reference" when constructing {@link JsonLocation} instances.
     *
     * @since 2.9
     */
    protected Object _getSourceReference() {
        if (JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION.enabledIn(_features)) {
            return _ioContext.getSourceReference();
        }
        return null;
    }
}

