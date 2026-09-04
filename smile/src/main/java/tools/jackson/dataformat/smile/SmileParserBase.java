package tools.jackson.dataformat.smile;

import java.math.BigDecimal;
import java.math.BigInteger;

import tools.jackson.core.*;
import tools.jackson.core.base.ParserMinimalBase;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.json.DupDetector;
import tools.jackson.core.sym.ByteQuadsCanonicalizer;
import tools.jackson.core.util.JacksonFeatureSet;
import tools.jackson.core.util.SimpleStreamReadContext;
import tools.jackson.core.util.TextBuffer;

public abstract class SmileParserBase extends ParserMinimalBase
{
    protected final static String[] NO_STRINGS = new String[0];

    // Avoid OOME/DoS for bigger binary; read eagerly only up to 250k
    protected final static int LONGEST_NON_CHUNKED_BINARY = 250_000;

    // @since 2.16
    protected final static int DEFAULT_NAME_BUFFER_LENGTH = 64;    

    // @since 2.16
    protected final static int DEFAULT_STRING_VALUE_BUFFER_LENGTH = 64;

    protected final static JacksonFeatureSet<StreamReadCapability> SMILE_READ_CAPABILITIES
        = DEFAULT_READ_CAPABILITIES.with(StreamReadCapability.EXACT_FLOATS);

    /**
     * Whether {@code VarHandle}-based array access is usable on this runtime;
     * probed once at class load. See {@link #_decodeQuad}.
     *
     * @since 3.3
     */
    private final static boolean _VARHANDLE_AVAILABLE = _checkVarHandleAvailable();

    private static boolean _checkVarHandleAvailable() {
        // NOTE: this call is what first loads `SmileVarHandleUtil`, and that class
        // names `VarHandle` in its field/method signatures. On a runtime without
        // `java.lang.invoke.VarHandle` (some Android builds) loading it raises
        // `NoClassDefFoundError` -- an Error, not an Exception -- so `Throwable`
        // is what has to be caught here.
        try {
            return SmileVarHandleUtil.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /*
    /**********************************************************************
    /* Config
    /**********************************************************************
     */

    /**
     * Bit flag composed of bits that indicate which
     * {@link SmileReadFeature}s are enabled.
     *<p>
     * NOTE: currently the only feature ({@link SmileReadFeature#REQUIRE_HEADER}
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
     * (blocks that were already processed prior to the current buffer).
     */
    protected long _currInputProcessed;

    /**
     * Alternative to {@code _tokenInputTotal} that will only contain
     * offset within input buffer, as int.
     */
    protected int _tokenOffsetForTotal;

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected SimpleStreamReadContext _streamReadContext;

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

    protected long _numberLong;

    protected float _numberFloat;

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

    public SmileParserBase(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, int formatFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(readCtxt, ioCtxt, parserFeatures);
        _formatFeatures = formatFeatures;
        _symbols = sym;
        _symbolsCanonical = sym.isCanonicalizing();
        DupDetector dups = StreamReadFeature.STRICT_DUPLICATE_DETECTION.enabledIn(parserFeatures)
                ? DupDetector.rootDetector(this) : null;
        _streamReadContext = SimpleStreamReadContext.createRootContext(dups);
        _textBuffer = ioCtxt.constructReadConstrainedTextBuffer();
    }

    /*
    /**********************************************************************
    /* Versioned
    /**********************************************************************
     */

    @Override
    public final Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Capability, config introspection
    /**********************************************************************
     */

    @Override
    public JacksonFeatureSet<StreamReadCapability> streamReadCapabilities() {
        return SMILE_READ_CAPABILITIES;
    }

    // @since 3.2
    @Override
    public boolean willInternPropertyNames() {
        return _symbols.willInternStrings();
    }

    public final boolean mayContainRawBinary() {
        return _mayContainRawBinary;
    }

    /*
    /**********************************************************************
    /* Abstract methods for sub-classes to provide
    /**********************************************************************
     */

    @Override
    protected abstract void _closeInput() throws JacksonException;

    /**
     * Method called to complete parsing of a numeric value: will throw
     * {@link StreamReadException} if current token is not numeric.
     *
     * @throws StreamReadException if current token is not numeric
     */
    protected abstract void _parseNumericValue() throws JacksonException;

    /**
     * Similar to {@link #_parseNumericValue()}, but will not throw exception
     * if the current token is not a numeric value.
     *
     * @return {@code true} if current token is numeric; {@code false} otherwise
     */
    protected abstract boolean _parseNumericValueIfNumber() throws JacksonException;
    
//  public abstract int releaseBuffered(OutputStream out) throws JacksonException;
//  public abstract Object getInputSource();

    /*
    /**********************************************************************
    /* Abstract impls, simple accessors
    /**********************************************************************
     */

    @Override public final SimpleStreamReadContext streamReadContext() { return _streamReadContext; }
    @Override public void assignCurrentValue(Object v) { _streamReadContext.assignCurrentValue(v); }
    @Override public Object currentValue() { return _streamReadContext.currentValue(); }
    @Override public final boolean isClosed() { return _closed; }

    /*
    /**********************************************************************
    /* Abstract impls, Location access
    /**********************************************************************
     */

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public final TokenStreamLocation currentTokenLocation()
    {
        // token location is correctly managed...
        long total = _currInputProcessed + _tokenOffsetForTotal;
        // 2.4: used to be: _tokenInputTotal
        return new TokenStreamLocation(_ioContext.contentReference(),
                total, // bytes
                -1, -1, (int) total); // char offset, line, column
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public final TokenStreamLocation currentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new TokenStreamLocation(_ioContext.contentReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public final String currentName()
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            return _streamReadContext.getParent().currentName();
        }
        return _streamReadContext.currentName();
    }

    /*
    /**********************************************************************
    /* Abstract impls, I/O state
    /**********************************************************************
     */

    @Override
    public final void close() throws JacksonException {
        super.close();
        _inputEnd = 0;
        _symbols.release();
    }

    @Override
    protected final void _releaseBuffers() {
        _textBuffer.releaseBuffers();
        _releaseBuffers2();
    }

    protected abstract void _releaseBuffers2();

    /*
    /**********************************************************************
    /* Numeric accessors of public API
    /**********************************************************************
     */

    @Override
    public final boolean isNaN() throws JacksonException {
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
    public final Number getNumberValue() throws JacksonException
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
    public final Number getNumberValueExact() throws JacksonException {
        return getNumberValue();
    }

    @Override
    public final NumberType getNumberType() throws JacksonException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            if (!_parseNumericValueIfNumber()) {
                return null;
            }
        }
        return _numberType;
    }

    @Override // since 2.17
    public NumberTypeFP getNumberTypeFP() throws JacksonException {
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
    public final int getIntValue() throws JacksonException
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
    public final long getLongValue() throws JacksonException
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
    public final BigInteger getBigIntegerValue() throws JacksonException
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
    public final float getFloatValue() throws JacksonException
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
    public final double getDoubleValue() throws JacksonException
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
    public final BigDecimal getDecimalValue() throws JacksonException
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
    /**********************************************************************
    /* Numeric conversions
    /**********************************************************************
     */

    protected final void convertNumberToInt() throws JacksonException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportOverflowInt(String.valueOf(_numberLong));
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                _reportOverflowInt(String.valueOf(_numberBigInt));
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                _reportOverflowInt(String.valueOf(_numberDouble));
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                _reportOverflowInt(String.valueOf(_numberFloat));
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                _reportOverflowInt(String.valueOf(_numberBigDecimal));
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }

    protected final void convertNumberToLong() throws JacksonException
    {
        int v = _numTypesValid;
        if ((v & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((v & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                _reportOverflowLong(String.valueOf(_numberBigInt));
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((v & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                _reportOverflowLong(String.valueOf(_numberDouble));
            }
            _numberLong = (long) _numberDouble;
        } else if ((v & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                _reportOverflowLong(String.valueOf(_numberFloat));
            }
            _numberLong = (long) _numberFloat;
        } else if ((v & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                _reportOverflowLong(String.valueOf(_numberBigDecimal));
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }

    protected final void convertNumberToBigInteger() throws JacksonException
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

    protected final void convertNumberToFloat() throws JacksonException
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

    protected final void convertNumberToDouble() throws JacksonException
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

    protected final void convertNumberToBigDecimal() throws JacksonException
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
    /**********************************************************************
    /* Internal/package methods: other
    /**********************************************************************
     */

    /**
     * Method called when an EOF is encountered between tokens.
     * If so, it may be a legitimate EOF, but only iff there
     * is no open non-root context.
     */
    @Override
    protected void _handleEOF() throws StreamReadException {
        if (!_streamReadContext.inRoot()) {
            String marker = _streamReadContext.inArray() ? "Array" : "Object";
            _reportInvalidEOF(String.format(
                    ": expected close marker for %s (start marker at %s)",
                    marker,
                    _streamReadContext.startLocation(_sourceReference())),
                    null);
        }
    }

    protected void _reportMismatchedEndMarker(int actCh, char expCh) throws StreamReadException {
        TokenStreamContext ctxt = streamReadContext();
        _reportError(String.format(
                "Unexpected close marker '%s': expected '%c' (for %s starting at %s)",
                (char) actCh, expCh, ctxt.typeDesc(), ctxt.startLocation(_sourceReference())));
    }

    /**
     * Helper method for decoding 4 bytes of an Object property name into the
     * "quad" (big-endian {@code int}) form used by {@link ByteQuadsCanonicalizer}.
     *<p>
     * Uses a single unaligned load via {@code VarHandle} where supported,
     * falling back to byte shifting otherwise. Since {@link #_VARHANDLE_AVAILABLE}
     * is a {@code static final} the branch folds away at JIT time; and on the
     * fallback path {@code SmileVarHandleUtil} is never resolved, which is what
     * keeps this working on runtimes that lack {@code VarHandle} entirely.
     *<p>
     * Exists on the base class so that subclasses in the {@code async} package
     * can share the same implementation; caller MUST have verified that 4 bytes
     * are readable at given offset.
     *
     * @since 3.3
     */
    protected final static int _decodeQuad(byte[] buffer, int offset) {
        if (_VARHANDLE_AVAILABLE) {
            return SmileVarHandleUtil.getIntBE(buffer, offset);
        }
        return ((buffer[offset] & 0xFF) << 24)
                | ((buffer[offset+1] & 0xFF) << 16)
                | ((buffer[offset+2] & 0xFF) << 8)
                | (buffer[offset+3] & 0xFF);
    }

    /**
     * Helper method for decoding the trailing 1 - 4 bytes of an Object property
     * name into a right-aligned, zero-padded "quad": that is, produces the same
     * value as accumulating {@code len} bytes with 8-bit shifts would.
     *<p>
     * Reads all 4 bytes with a single load where it can, which may read up to 3
     * bytes past {@code offset+len}. Those bytes are shifted out and cannot
     * affect the result, but the read still has to stay within the array: hence
     * the length check, which also selects the byte-shifting path for a name
     * that ends within 3 bytes of the end of the buffer.
     *<p>
     * Caller MUST have verified that {@code len} bytes are readable at given
     * offset, and that {@code len} is between 1 and 4.
     *
     * @since 3.3
     */
    protected final static int _decodePartialQuad(byte[] buffer, int offset, int len) {
        if ((offset + 4) <= buffer.length) {
            return _decodeQuad(buffer, offset) >>> ((4 - len) << 3);
        }
        int q = buffer[offset] & 0xFF;
        if (len > 1) {
            q = (q << 8) | (buffer[offset+1] & 0xFF);
            if (len > 2) {
                q = (q << 8) | (buffer[offset+2] & 0xFF);
                if (len > 3) {
                    q = (q << 8) | (buffer[offset+3] & 0xFF);
                }
            }
        }
        return q;
    }

    /**
     * Helper method that pads the unused high bytes of a partial quad with 1s
     * rather than 0s ([dataformats-binary#312]).
     *
     * @param bytes Number of bytes actually decoded; must be between 1 and 4
     */
    protected final static int _padLastQuad(int q, int bytes) {
        return (bytes == 4) ? q : (q | (-1 << (bytes << 3)));
    }

    /**
     * Variant of {@link #_decodePartialQuad} that pads the unused high bytes with
     * 1s rather than 0s, which is what {@link ByteQuadsCanonicalizer} expects of a
     * partial quad: without it a name ending in NULL bytes would collide with the
     * shorter name that precedes those NULLs.
     *<p>
     * Shared by blocking and non-blocking parsers ([dataformats-binary#761]).
     *
     * @param len Number of bytes to decode; must be between 1 and 4
     *
     * @since 3.3
     */
    protected final static int _decodePartialQuadForNulls(byte[] buffer, int offset, int len) {
        return _padLastQuad(_decodePartialQuad(buffer, offset, len), len);
    }

    /**
     * Helper method used to encapsulate logic of including (or not) of
     * "source reference" when constructing {@link TokenStreamLocation} instances.
     */
    protected ContentReference _sourceReference() {
        if (isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)) {
            return _ioContext.contentReference();
        }
        return ContentReference.unknown();
    }
}
