package com.fasterxml.jackson.dataformat.smile.async;

import java.io.*;
import java.lang.ref.SoftReference;
//import java.math.BigDecimal;
//import java.math.BigInteger;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.dataformat.smile.*;

import static com.fasterxml.jackson.dataformat.smile.SmileConstants.BYTE_MARKER_END_OF_STRING;

public abstract class NonBlockingParserBase
    extends ParserBase
    implements NonBlockingParser<NonBlockingByteArrayFeeder>,
        NonBlockingByteArrayFeeder
{
    protected final static byte[] NO_BYTES = new byte[0];
    protected final static int[] NO_INTS = new int[0];
    protected final static String[] NO_STRINGS = new String[0];

    // // // !!! TO BE REMOVED
    
    protected boolean _tokenIncomplete;
    protected byte[] _inputBuffer = NO_BYTES; // promote up
    
    /*
    /**********************************************************************
    /* Major state constants
    /**********************************************************************
     */

    /**
     * State right after parser has been constructed: waiting for header
     * (which may or may not be mandatory).
     */
    protected final static int MAJOR_INITIAL = 0;

    /**
     * State for recognized header marker, either in-feed or initial.
     */
    protected final static int MAJOR_HEADER = 1;

    protected final static int MAJOR_OBJECT_START = 2;
    protected final static int MAJOR_OBJECT_FIELD = 3;
    protected final static int MAJOR_OBJECT_VALUE = 4;

    protected final static int MAJOR_ARRAY_START = 5;
    protected final static int MAJOR_ARRAY_ELEMENT = 6;

    protected final static int MAJOR_VALUE_BINARY_RAW = 10;
    protected final static int MAJOR_VALUE_BINARY_7BIT = 11;

    protected final static int MAJOR_VALUE_STRING_SHORT_ASCII = 15;
    protected final static int MAJOR_VALUE_STRING_SHORT_UNICODE = 16;
    protected final static int MAJOR_VALUE_STRING_LONG_ASCII = 17;
    protected final static int MAJOR_VALUE_STRING_LONG_UNICODE = 18;
    protected final static int MAJOR_VALUE_STRING_SHARED_LONG = 19;
    
    protected final static int MAJOR_VALUE_NUMBER_INT = 20;
    protected final static int MAJOR_VALUE_NUMBER_LONG = 21;
    protected final static int MAJOR_VALUE_NUMBER_FLOAT = 22;
    protected final static int MAJOR_VALUE_NUMBER_DOUBLE = 23;
    protected final static int MAJOR_VALUE_NUMBER_BIGINT = 24;
    protected final static int MAJOR_VALUE_NUMBER_BIGDEC = 25;

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    /**
     * Flag that indicates whether content can legally have raw (unquoted)
     * binary data. Since this information is included both in header and
     * in actual binary data blocks there is redundancy, and we want to
     * ensure settings are compliant. Using application may also want to
     * know this setting in case it does some direct (random) access.
     */
    protected boolean _mayContainRawBinary;

    protected final boolean _cfgRequireHeader;

    /**
     * Helper object used for low-level recycling of Smile-generator
     * specific buffers.
     */
    final protected SmileBufferRecycler<String> _smileBufferRecycler;

    /*
    /**********************************************************************
    /* Additional parsing state
    /**********************************************************************
     */

    /**
     * Current main decoding state
     */
    protected int _majorState;

    /**
     * Addition indicator within state; contextually relevant for just that state
     */
    protected int _minorState;

    /**
     * Specific flag that is set when we encountered a 32-bit
     * floating point value; needed since numeric super classes do
     * not track distinction between float and double, but Smile
     * format does, and we want to retain that separation.
     */
    protected boolean _got32BitFloat;

    /**
     * For 32-bit values, we may use this for combining values
     */
    protected int _pendingInt;

    /**
     * For 64-bit values, we may use this for combining values
     */
    protected long _pendingLong;
    
    /**
     * Flag that is sent when calling application indicates that there will
     * be no more input to parse.
     */
    protected boolean _endOfInput = false;
    
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

    /*
    /**********************************************************************
    /* Name/entity parsing state
    /**********************************************************************
     */

    /**
     * Number of complete quads parsed for current name (quads
     * themselves are stored in {@link #_quadBuffer}).
     */
    protected int _quadCount;

    /**
     * Bytes parsed for the current, incomplete, quad
     */
    protected int _currQuad;

    /**
     * Number of bytes pending/buffered, stored in {@link #_currQuad}
     */
    protected int _currQuadBytes = 0;
     
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
    /* Thread-local recycling
    /**********************************************************************
     */
    
    /**
     * <code>ThreadLocal</code> contains a {@link java.lang.ref.SoftReference}
     * to a buffer recycler used to provide a low-cost
     * buffer recycling for Smile-specific buffers.
     */
    final protected static ThreadLocal<SoftReference<SmileBufferRecycler<String>>> _smileRecyclerRef
        = new ThreadLocal<SoftReference<SmileBufferRecycler<String>>>();
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public NonBlockingParserBase(IOContext ctxt, int parserFeatures, int smileFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(ctxt, parserFeatures);        
        _symbols = sym;
        
        _tokenInputRow = -1;
        _tokenInputCol = -1;
        _smileBufferRecycler = _smileBufferRecycler();

        _currToken = JsonToken.NOT_AVAILABLE;
        _majorState = MAJOR_INITIAL;

        _cfgRequireHeader = (smileFeatures & SmileParser.Feature.REQUIRE_HEADER.getMask()) != 0;
    }

    @Override
    public ObjectCodec getCodec() {
        return null;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        throw new UnsupportedOperationException("Can not use ObjectMapper with non-blocking parser");
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
    /* Former StreamBasedParserBase methods
    /**********************************************************************
     */

    @Override
    public int releaseBuffered(OutputStream out) throws IOException {
        // 04-May-2017, tatu: Although we may actually kind of have fed input, this
        //    is not a good way to retrieve it. Should we throw exception?
        return 0;
    }
    
    @Override
    public Object getInputSource() {
        // since input is "pushed", to traditional source...
        return null;
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation getTokenLocation()
    {
        // token location is correctly managed...
        return new JsonLocation(_ioContext.getSourceReference(),
                _tokenInputTotal, // bytes
                -1, -1, (int) _tokenInputTotal); // char offset, line, column
    }   

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation getCurrentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new JsonLocation(_ioContext.getSourceReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /*
    /**********************************************************************
    /* Low-level reading, other
    /**********************************************************************
     */

    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected final boolean _loadToHaveAtLeast(int minAvailable) throws IOException
    {
        _throwInternal();
        return false;
    }
    
    @Override
    protected void _closeInput() throws IOException {
        // nothing to do here
    }
    
    /*
    /**********************************************************************
    /* Overridden methods
    /**********************************************************************
     */

    @Override
    public void close() throws IOException
    {
        super.close();
        // Merge found symbols, if any:
        _symbols.release();
    }

    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            // yes; is or can be made available efficiently as char[]
            return _textBuffer.hasTextAsCharacters();
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            // not necessarily; possible but:
            return _nameCopied;
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
    protected void _releaseBuffers() throws IOException
    {
        super._releaseBuffers();
        {
            String[] nameBuf = _seenNames;
            if (nameBuf != null && nameBuf.length > 0) {
                _seenNames = null;
                // Need to clear the buffer;
                // but we only need to clear up to count as it is not a hash area
                if (_seenNameCount > 0) {
                    Arrays.fill(nameBuf, 0, _seenNameCount, null);
                }
                _smileBufferRecycler.releaseSeenNamesBuffer(nameBuf);
            }
        }
        {
            String[] valueBuf = _seenStringValues;
            if (valueBuf != null && valueBuf.length > 0) {
                _seenStringValues = null;
                // Need to clear the buffer;
                // but we only need to clear up to count as it is not a hash area
                if (_seenStringValueCount > 0) {
                    Arrays.fill(valueBuf, 0, _seenStringValueCount, null);
                }
                _smileBufferRecycler.releaseSeenStringValuesBuffer(valueBuf);
            }
        }
    }
    
    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
     */

    public boolean mayContainRawBinary() {
        return _mayContainRawBinary;
    }

    /*
    /**********************************************************************
    /* JsonParser impl
    /**********************************************************************
     */

    protected final JsonToken _handleSharedString(int index) throws IOException
    {
        if (index >= _seenStringValueCount) {
            _reportInvalidSharedStringValue(index);
        }
        _textBuffer.resetWithString(_seenStringValues[index]);
        return (_currToken = JsonToken.VALUE_STRING);
    }

    protected final void _addSeenStringValue() throws IOException
    {
        _finishToken();
        if (_seenStringValueCount < _seenStringValues.length) {
            // !!! TODO: actually only store char[], first time around?
            _seenStringValues[_seenStringValueCount++] = _textBuffer.contentsAsString();
            return;
        }
        _expandSeenStringValues();
    }

    private final void _expandSeenStringValues()
    {
        String[] oldShared = _seenStringValues;
        int len = oldShared.length;
        String[] newShared;
        if (len == 0) {
            newShared = _smileBufferRecycler.allocSeenStringValuesBuffer();
            if (newShared == null) {
                newShared = new String[SmileBufferRecycler.DEFAULT_STRING_VALUE_BUFFER_LENGTH];
            }
        } else if (len == SmileConstants.MAX_SHARED_STRING_VALUES) { // too many? Just flush...
           newShared = oldShared;
           _seenStringValueCount = 0; // could also clear, but let's not yet bother
        } else {
            int newSize = (len == SmileBufferRecycler.DEFAULT_NAME_BUFFER_LENGTH) ? 256 : SmileConstants.MAX_SHARED_STRING_VALUES;
            newShared = new String[newSize];
            System.arraycopy(oldShared, 0, newShared, 0, oldShared.length);
        }
        _seenStringValues = newShared;
        _seenStringValues[_seenStringValueCount++] = _textBuffer.contentsAsString();
    }

    @Override
    public String getCurrentName() throws IOException
    {
        return _parsingContext.getCurrentName();
    }

    @Override
    public NumberType getNumberType()
        throws IOException
    {
        if (_got32BitFloat) {
            return NumberType.FLOAT;
        }
        return super.getNumberType();
    }
    
    /*
    /**********************************************************************
    /* NonBlockParser impl (except for NonBlockingInputFeeder)
    /**********************************************************************
     */

    @Override
    public NonBlockingByteArrayFeeder getInputFeeder() {
        return this;
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing:
    /**********************************************************************
     */

    protected final JsonToken _nextInt(int substate, int value) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                value = (value << 6) | (b & 0x3F);
                _numberInt = SmileUtil.zigzagDecode(value);
                _numTypesValid = NR_INT;
                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_INT);
            }
            // can't get too big; 5 bytes is max
            if (++substate >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _minorState = substate;
        _pendingInt = value;
        _majorState = MAJOR_VALUE_NUMBER_INT;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextLong(int substate, long value) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                value = (value << 6) | (b & 0x3F);
                _numberLong = SmileUtil.zigzagDecode(value);
                _numTypesValid = NR_LONG;
                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_INT);
            }
            // can't get too big; 10 bytes is max
            if (++substate >=  10) {
                _reportError("Corrupt input; 64-bit VInt extends beyond 10 data bytes");
            }
            value = (value << 7) | b;
        }
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _minorState = substate;
        _pendingLong = value;
        _majorState = MAJOR_VALUE_NUMBER_LONG;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextBigInt(int substate) throws IOException
    {
        // !!! TBI
        _tokenIncomplete = true;
        _minorState = substate;
//        _pendingLong = value;
        _majorState = MAJOR_VALUE_NUMBER_BIGDEC;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    private final boolean _finishBigInteger()
        throws IOException
    {
        byte[] raw = _read7BitBinaryWithLength();
        if (raw == null) {
            return false;
        }
        _numberBigInt = new BigInteger(raw);
        _numTypesValid = NR_BIGINT;
        return true;
    }
*/
    
    protected final JsonToken _nextFloat(int substate, int value) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            value = (value << 7) + b;
            if (++substate == 5) { // done!
                _numberDouble = (double) Float.intBitsToFloat(value);
                _numTypesValid = NR_DOUBLE;
                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            }
        }
        _tokenIncomplete = true;
        _minorState = substate;
        _pendingInt = value;
        _majorState = MAJOR_VALUE_NUMBER_FLOAT;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextDouble(int substate, long value) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            value = (value << 7) + b;
            if (++substate == 10) { // done!
                _numberDouble = Double.longBitsToDouble(value);
                _numTypesValid = NR_DOUBLE;
                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            }
        }
        _tokenIncomplete = true;
        _minorState = substate;
        _pendingLong = value;
        _majorState = MAJOR_VALUE_NUMBER_DOUBLE;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextBigDecimal(int substate) throws IOException
    {
        // !!! TBI
        _tokenIncomplete = true;
        _minorState = substate;
//        _pendingLong = value;
        _majorState = MAJOR_VALUE_NUMBER_BIGDEC;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
/*
    private final void _finishBigDecimal()
        throws IOException
    {
        int scale = SmileUtil.zigzagDecode(_readUnsignedVInt());
        byte[] raw = _read7BitBinaryWithLength();
        _numberBigDecimal = new BigDecimal(new BigInteger(raw), scale);
        _numTypesValid = NR_BIGDECIMAL;
    }
    
 */

    /*
    private final int _readUnsignedVInt()
        throws IOException
    {
        int value = 0;
        while (true) {
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            int i = _inputBuffer[_inputPtr++];
            if (i < 0) { // last byte
                value = (value << 6) + (i & 0x3F);
                return value;
            }
            value = (value << 7) + i;
        }
    }
    */
    
    protected final boolean _handleHeader(int substate) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            byte b = _inputBuffer[_inputPtr++];
            switch (substate) {
            case 0: // after first byte
                if (b != SmileConstants.HEADER_BYTE_2) {
                    _reportError("Malformed content: header signature not valid, starts with 0x3a but followed by 0x"
                            +Integer.toHexString(_inputBuffer[_inputPtr] & 0xFF)+", not 0x29");
                }
                break;
            case 1:
                if (b != SmileConstants.HEADER_BYTE_3) {
                    _reportError("Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x"
                            +Integer.toHexString(_inputBuffer[_inputPtr & 0xFF])+", not 0x0A");
                }
                break;
            case 2: // ok, here be the version, config bits...
                int versionBits = (b >> 4) & 0x0F;
                // but failure with version number is fatal, can not ignore
                if (versionBits != SmileConstants.HEADER_VERSION_0) {
                    _reportError("Header version number bits (0x"+Integer.toHexString(versionBits)+") indicate unrecognized version; only 0x0 handled by parser");
                }

                // can avoid tracking names, if explicitly disabled
                if ((b & SmileConstants.HEADER_BIT_HAS_SHARED_NAMES) == 0) {
                    _seenNames = null;
                    _seenNameCount = -1;
                }
                // conversely, shared string values must be explicitly enabled
                if ((b & SmileConstants.HEADER_BIT_HAS_SHARED_STRING_VALUES) != 0) {
                    _seenStringValues = NO_STRINGS;
                    _seenStringValueCount = 0;
                }
                _mayContainRawBinary = ((b & SmileConstants.HEADER_BIT_HAS_RAW_BINARY) != 0);
                _tokenIncomplete = false;
                return true;
            }
        }
        _tokenIncomplete = true;
        _majorState = MAJOR_HEADER;
        _minorState = substate;
        return false;
    }

    protected final JsonToken _nextShortAscii(int substate) throws IOException
    {
        _majorState = MAJOR_VALUE_STRING_SHORT_ASCII;
        _tokenIncomplete = true;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextShortUnicode(int substate) throws IOException
    {
        _majorState = MAJOR_VALUE_STRING_SHORT_UNICODE;
        _tokenIncomplete = true;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    /*
    protected final void _decodeShortAsciiValue(int len)
        throws IOException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        // Note: we count on fact that buffer must have at least 'len' (<= 64) empty char slots
        final char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;

        // meaning: regular tight loop is no slower, typically faster here:
        for (final int end = inPtr + len; inPtr < end; ++inPtr) {
            outBuf[outPtr++] = (char) inBuf[inPtr];            
        }
        
        _inputPtr = inPtr;
        _textBuffer.setCurrentLength(len);
    }

    protected final void _decodeShortUnicodeValue(int len)
        throws IOException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        final byte[] inputBuf = _inputBuffer;
        for (int end = inPtr + len; inPtr < end; ) {
            int i = inputBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // trickiest one, need surrogate handling
                switch (code) {
                case 1:
                    i = ((i & 0x1F) << 6) | (inputBuf[inPtr++] & 0x3F);
                    break;
                case 2:
                    i = ((i & 0x0F) << 12)
                          | ((inputBuf[inPtr++] & 0x3F) << 6)
                          | (inputBuf[inPtr++] & 0x3F);
                    break;
                case 3:
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
                    _reportError("Invalid byte "+Integer.toHexString(i)+" in short Unicode text block");
                }
            }
            outBuf[outPtr++] = (char) i;
        }        
        _textBuffer.setCurrentLength(outPtr);
    }
     */
    
    protected final JsonToken _nextLongAscii(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
        _majorState = MAJOR_VALUE_STRING_LONG_ASCII;
        _tokenIncomplete = true;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    private final void _decodeLongAscii()
        throws IOException
    {
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        main_loop:
        while (true) {
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            int inPtr = _inputPtr;
            int left = _inputEnd - inPtr;
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            left = Math.min(left, outBuf.length - outPtr);
            do {
                byte b = _inputBuffer[inPtr++];
                if (b == SmileConstants.BYTE_MARKER_END_OF_STRING) {
                    _inputPtr = inPtr;
                    break main_loop;
                }
                outBuf[outPtr++] = (char) b;                    
            } while (--left > 0);
            _inputPtr = inPtr;
        }
        _textBuffer.setCurrentLength(outPtr);
    }
    */

    protected final JsonToken _nextLongUnicode(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
        _majorState = MAJOR_VALUE_STRING_LONG_UNICODE;
        _tokenIncomplete = true;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    
/*
    private final void _decodeLongUnicode()
        throws IOException
    {
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        int c;
        final byte[] inputBuffer = _inputBuffer;

        main_loop:
        while (true) {
            // First the tight ASCII loop:
            ascii_loop:
            while (true) {
                int ptr = _inputPtr;
                if (ptr >= _inputEnd) {
                    _loadMoreGuaranteed();
                    ptr = _inputPtr;
                }
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                int max = _inputEnd;
                {
                    int max2 = ptr + (outBuf.length - outPtr);
                    if (max2 < max) {
                        max = max2;
                    }
                }
                while (ptr < max) {
                    c = (int) inputBuffer[ptr++] & 0xFF;
                    if (codes[c] != 0) {
                        _inputPtr = ptr;
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (char) c;
                }
                _inputPtr = ptr;
            }
            // Ok: end marker, escape or multi-byte?
            if (c == SmileConstants.INT_MARKER_END_OF_STRING) {
                break main_loop;
            }

            switch (codes[c]) {
            case 1: // 2-byte UTF
                c = _decodeUtf8_2(c);
                break;
            case 2: // 3-byte UTF
                if ((_inputEnd - _inputPtr) >= 2) {
                    c = _decodeUtf8_3fast(c);
                } else {
                    c = _decodeUtf8_3(c);
                }
                break;
            case 3: // 4-byte UTF
                c = _decodeUtf8_4(c);
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }
     */
    
    protected final JsonToken _nextLongSharedString(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _majorState = MAJOR_VALUE_STRING_SHARED_LONG;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextRawBinary(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _majorState = MAJOR_VALUE_BINARY_RAW;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

/*

    private final void _finishRawBinary()
        throws IOException
    {
        int byteLen = _readUnsignedVInt();
        _binaryValue = new byte[byteLen];
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int ptr = 0;
        while (true) {
            int toAdd = Math.min(byteLen, _inputEnd - _inputPtr);
            System.arraycopy(_inputBuffer, _inputPtr, _binaryValue, ptr, toAdd);
            _inputPtr += toAdd;
            ptr += toAdd;
            byteLen -= toAdd;
            if (byteLen <= 0) {
                return;
            }
            _loadMoreGuaranteed();
        }
    }
 */
    
    protected final JsonToken _nextQuotedBinary(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _majorState = MAJOR_VALUE_BINARY_7BIT;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    /*
    private final byte[] _read7BitBinaryWithLength()
        throws IOException
    {
        int byteLen = _readUnsignedVInt();
        byte[] result = new byte[byteLen];
        int ptr = 0;
        int lastOkPtr = byteLen - 7;
        
        // first, read all 7-by-8 byte chunks
        while (ptr <= lastOkPtr) {
            if ((_inputEnd - _inputPtr) < 8) {
                _loadToHaveAtLeast(8);
            }
            int i1 = (_inputBuffer[_inputPtr++] << 25)
                + (_inputBuffer[_inputPtr++] << 18)
                + (_inputBuffer[_inputPtr++] << 11)
                + (_inputBuffer[_inputPtr++] << 4);
            int x = _inputBuffer[_inputPtr++];
            i1 += x >> 3;
            int i2 = ((x & 0x7) << 21)
                + (_inputBuffer[_inputPtr++] << 14)
                + (_inputBuffer[_inputPtr++] << 7)
                + _inputBuffer[_inputPtr++];
            // Ok: got our 7 bytes, just need to split, copy
            result[ptr++] = (byte)(i1 >> 24);
            result[ptr++] = (byte)(i1 >> 16);
            result[ptr++] = (byte)(i1 >> 8);
            result[ptr++] = (byte)i1;
            result[ptr++] = (byte)(i2 >> 16);
            result[ptr++] = (byte)(i2 >> 8);
            result[ptr++] = (byte)i2;
        }
        // and then leftovers: n+1 bytes to decode n bytes
        int toDecode = (result.length - ptr);
        if (toDecode > 0) {
            if ((_inputEnd - _inputPtr) < (toDecode+1)) {
                _loadToHaveAtLeast(toDecode+1);
            }
            int value = _inputBuffer[_inputPtr++];
            for (int i = 1; i < toDecode; ++i) {
                value = (value << 7) + _inputBuffer[_inputPtr++];
                result[ptr++] = (byte) (value >> (7 - i));
            }
            // last byte is different, has remaining 1 - 6 bits, right-aligned
            value <<= toDecode;
            result[ptr] = (byte) (value + _inputBuffer[_inputPtr++]);
        }
        return result;
    }
     */

    /*
    /**********************************************************************
    /* Public API, traversal, nextXxxValue/nextFieldName
    /**********************************************************************
     */

    /* Implementing these methods efficiently for non-blocking cases would
     * be complicated; so for now let's just use the default non-optimized
     * implementation
     */
    
//    public boolean nextFieldName(SerializableString str) throws IOException
//    public String nextTextValue() throws IOException
//    public int nextIntValue(int defaultValue) throws IOException
//    public long nextLongValue(long defaultValue) throws IOException
//    public Boolean nextBooleanValue() throws IOException
    
    /*
    /**********************************************************************
    /* Public API, access to token information, text
    /**********************************************************************
     */

    /**
     * Method for accessing textual representation of the current event;
     * if no current event (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any event.
     */
    @Override
    public String getText() throws IOException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        if (_tokenIncomplete) {
            return null;
        }
        JsonToken t = _currToken;
        if (t == null) { // null only before/after document
            return null;
        }
        if (t == JsonToken.FIELD_NAME) {
            return _parsingContext.getCurrentName();
        }
        if (t.isNumeric()) {
            // TODO: optimize?
            return getNumberValue().toString();
        }
        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            switch (_currToken) {                
            case VALUE_STRING:
                return _textBuffer.getTextBuffer();
            case FIELD_NAME:
                if (!_nameCopied) {
                    String name = _parsingContext.getCurrentName();
                    int nameLen = name.length();
                    if (_nameCopyBuffer == null) {
                        _nameCopyBuffer = _ioContext.allocNameCopyBuffer(nameLen);
                    } else if (_nameCopyBuffer.length < nameLen) {
                        _nameCopyBuffer = new char[nameLen];
                    }
                    name.getChars(0, nameLen, _nameCopyBuffer, 0);
                    _nameCopied = true;
                }
                return _nameCopyBuffer;

                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                // TODO: optimize
                return getNumberValue().toString().toCharArray();
            default:
                if (_tokenIncomplete) {
                    return null;
                }
                return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override    
    public int getTextLength()
        throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                return -1; // or throw exception?
            }
            switch (_currToken) {
            case VALUE_STRING:
                return _textBuffer.size();                
            case FIELD_NAME:
                return _parsingContext.getCurrentName().length();
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                // TODO: optimize
                return getNumberValue().toString().length();
                
            default:
                return _currToken.asCharArray().length;
            }
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws IOException
    {
        return 0;
    }

    // !!! TODO: can this be supported reliably?
    @Override
    public int getText(Writer w) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    /*
    /**********************************************************************
    /* Public API, access to token information, binary
    /**********************************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant)
        throws IOException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            // Todo, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject()
        throws IOException
    {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    // could possibly implement this... or maybe not.
    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    /*
    /**********************************************************************
    /* Internal methods, field name parsing
    /**********************************************************************
     */

    /**
     * Method that handles initial token type recognition for token
     * that has to be either FIELD_NAME or END_OBJECT.
     */
    protected final JsonToken _handleFieldName() throws IOException
    {    	
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int ch = _inputBuffer[_inputPtr++];
        switch ((ch >> 6) & 3) {
        case 0: // misc, including end marker
            switch (ch) {
            case 0x20: // empty String as name, legal if unusual
                _parsingContext.setCurrentName("");
                return JsonToken.FIELD_NAME;
            case 0x30: // long shared
            case 0x31:
            case 0x32:
            case 0x33:
                {
                    if (_inputPtr >= _inputEnd) {
                        _loadMoreGuaranteed();
	            }
	            int index = ((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
                    if (index >= _seenNameCount) {
                        _reportInvalidSharedName(index);
                    }
	            _parsingContext.setCurrentName(_seenNames[index]);
	        }
                return JsonToken.FIELD_NAME;
            case 0x34: // long ASCII/Unicode name
                _handleLongFieldName();
                return JsonToken.FIELD_NAME;            	
            }
            break;
        case 1: // short shared, can fully process
            {
                int index = (ch & 0x3F);
                if (index >= _seenNameCount) {
                    _reportInvalidSharedName(index);
                }
                _parsingContext.setCurrentName(_seenNames[index]);
            }
            return JsonToken.FIELD_NAME;
        case 2: // short ASCII
	    {
	        int len = 1 + (ch & 0x3f);
	        String name = _findDecodedFromSymbols(len);
        	    if (name != null) {
        	        _inputPtr += len;
        	    } else {
        	        name = _decodeShortAsciiName(len);
        	        name = _addDecodedToSymbols(len, name);
        	    }
        	    if (_seenNames != null) {
        	        if (_seenNameCount >= _seenNames.length) {
        	            _seenNames = _expandSeenNames(_seenNames);
        	        }
        	        _seenNames[_seenNameCount++] = name;
        	    }
        	    _parsingContext.setCurrentName(name);
	    }
	    return JsonToken.FIELD_NAME;                
        case 3: // short Unicode
            // all valid, except for 0xFF
            ch &= 0x3F;
            {
                if (ch > 0x37) {
                    if (ch == 0x3B) {
                        if (!_parsingContext.inObject()) {
                            _reportMismatchedEndMarker('}', ']');
                        }
                        _parsingContext = _parsingContext.getParent();
                        return JsonToken.END_OBJECT;
                    }
                } else {
                    final int len = ch + 2; // values from 2 to 57...
                    String name = _findDecodedFromSymbols(len);
                    if (name != null) {
                        _inputPtr += len;
                    } else {
                        name = _decodeShortUnicodeName(len);
                        name = _addDecodedToSymbols(len, name);
                    }
                    if (_seenNames != null) {
                        if (_seenNameCount >= _seenNames.length) {
    	                    _seenNames = _expandSeenNames(_seenNames);
                        }
                        _seenNames[_seenNameCount++] = name;
                    }
                    _parsingContext.setCurrentName(name);
                    return JsonToken.FIELD_NAME;                
                }
            }
            break;
        }
        // Other byte values are illegal
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(_inputBuffer[_inputPtr-1])
                +" for expected field name (or END_OBJECT marker)");
        return null;
    }

    /**
     * Method called to try to expand shared name area to fit one more potentially
     * shared String. If area is already at its biggest size, will just clear
     * the area (by setting next-offset to 0)
     */
    private final String[] _expandSeenNames(String[] oldShared)
    {
        int len = oldShared.length;
        String[] newShared;
        if (len == 0) {
            newShared = _smileBufferRecycler.allocSeenNamesBuffer();
            if (newShared == null) {
                newShared = new String[SmileBufferRecycler.DEFAULT_NAME_BUFFER_LENGTH];                
            }
        } else if (len == SmileConstants.MAX_SHARED_NAMES) { // too many? Just flush...
      	   newShared = oldShared;
      	   _seenNameCount = 0; // could also clear, but let's not yet bother
        } else {
            int newSize = (len == SmileBufferRecycler.DEFAULT_STRING_VALUE_BUFFER_LENGTH) ? 256 : SmileConstants.MAX_SHARED_NAMES;
            newShared = new String[newSize];
            System.arraycopy(oldShared, 0, newShared, 0, oldShared.length);
        }
        return newShared;
    }
    
    private int _quad1, _quad2;
    
    
    private final String _addDecodedToSymbols(int len, String name)
    {
        if (len < 5) {
            return _symbols.addName(name, _quad1, 0);
        }
        if (len < 9) {
            return _symbols.addName(name, _quad1, _quad2);
        }
        int qlen = (len + 3) >> 2;
        return _symbols.addName(name, _quadBuffer, qlen);
    }

    private final String _decodeShortAsciiName(int len)
        throws IOException
    {
        // note: caller ensures we have enough bytes available
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;
        
        // loop unrolling seems to help here:
        for (int inEnd = inPtr + len - 3; inPtr < inEnd; ) {
            outBuf[outPtr++] = (char) inBuf[inPtr++];            
            outBuf[outPtr++] = (char) inBuf[inPtr++];            
            outBuf[outPtr++] = (char) inBuf[inPtr++];            
            outBuf[outPtr++] = (char) inBuf[inPtr++];            
        }
        int left = (len & 3);
        if (left > 0) {
            outBuf[outPtr++] = (char) inBuf[inPtr++];
            if (left > 1) {
                outBuf[outPtr++] = (char) inBuf[inPtr++];
                if (left > 2) {
                    outBuf[outPtr++] = (char) inBuf[inPtr++];
                }
            }
        } 
        _inputPtr = inPtr;
        _textBuffer.setCurrentLength(len);
        return _textBuffer.contentsAsString();
    }
    
    /**
     * Helper method used to decode short Unicode string, length for which actual
     * length (in bytes) is known
     * 
     * @param len Length between 1 and 64
     */
    private final String _decodeShortUnicodeName(int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        final byte[] inBuf = _inputBuffer;
        for (int end = inPtr + len; inPtr < end; ) {
            int i = inBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // trickiest one, need surrogate handling
                switch (code) {
                case 1:
                    i = ((i & 0x1F) << 6) | (inBuf[inPtr++] & 0x3F);
                    break;
                case 2:
                    i = ((i & 0x0F) << 12)
                        | ((inBuf[inPtr++] & 0x3F) << 6)
                        | (inBuf[inPtr++] & 0x3F);
                    break;
                case 3:
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
                    _reportError("Invalid byte "+Integer.toHexString(i)+" in short Unicode text block");
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        _textBuffer.setCurrentLength(outPtr);
        return _textBuffer.contentsAsString();
    }

    // note: slightly edited copy of UTF8StreamParser.addName()
    private final String _decodeLongUnicodeName(int[] quads, int byteLen, int quadLen) throws IOException
    {
        int lastQuadBytes = byteLen & 3;
        // Ok: must decode UTF-8 chars. No other validation SHOULD be needed (except bounds checks?)
        /* Note: last quad is not correctly aligned (leading zero bytes instead
         * need to shift a bit, instead of trailing). Only need to shift it
         * for UTF-8 decoding; need revert for storage (since key will not
         * be aligned, to optimize lookup speed)
         */
        int lastQuad;
    
        if (lastQuadBytes < 4) {
            lastQuad = quads[quadLen-1];
            // 8/16/24 bit left shift
            quads[quadLen-1] = (lastQuad << ((4 - lastQuadBytes) << 3));
        } else {
            lastQuad = 0;
        }

        char[] cbuf = _textBuffer.emptyAndGetCurrentSegment();
        int cix = 0;
    
        for (int ix = 0; ix < byteLen; ) {
            int ch = quads[ix >> 2]; // current quad, need to shift+mask
            int byteIx = (ix & 3);
            ch = (ch >> ((3 - byteIx) << 3)) & 0xFF;
            ++ix;
    
            if (ch > 127) { // multi-byte
                int needed;
                if ((ch & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                    ch &= 0x1F;
                    needed = 1;
                } else if ((ch & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                    ch &= 0x0F;
                    needed = 2;
                } else if ((ch & 0xF8) == 0xF0) { // 4 bytes; double-char with surrogates and all...
                    ch &= 0x07;
                    needed = 3;
                } else { // 5- and 6-byte chars not valid chars
                    _reportInvalidInitial(ch);
                    needed = ch = 1; // never really gets this far
                }
                if ((ix + needed) > byteLen) {
                    _reportInvalidEOF(" in long field name", JsonToken.FIELD_NAME);
                }
                
                // Ok, always need at least one more:
                int ch2 = quads[ix >> 2]; // current quad, need to shift+mask
                byteIx = (ix & 3);
                ch2 = (ch2 >> ((3 - byteIx) << 3));
                ++ix;
                
                if ((ch2 & 0xC0) != 0x080) {
                    _reportInvalidOther(ch2);
                }
                ch = (ch << 6) | (ch2 & 0x3F);
                if (needed > 1) {
                    ch2 = quads[ix >> 2];
                    byteIx = (ix & 3);
                    ch2 = (ch2 >> ((3 - byteIx) << 3));
                    ++ix;
                    
                    if ((ch2 & 0xC0) != 0x080) {
                        _reportInvalidOther(ch2);
                    }
                    ch = (ch << 6) | (ch2 & 0x3F);
                    if (needed > 2) { // 4 bytes? (need surrogates on output)
                        ch2 = quads[ix >> 2];
                        byteIx = (ix & 3);
                        ch2 = (ch2 >> ((3 - byteIx) << 3));
                        ++ix;
                        if ((ch2 & 0xC0) != 0x080) {
                            _reportInvalidOther(ch2 & 0xFF);
                        }
                        ch = (ch << 6) | (ch2 & 0x3F);
                    }
                }
                if (needed > 2) { // surrogate pair? once again, let's output one here, one later on
                    ch -= 0x10000; // to normalize it starting with 0x0
                    if (cix >= cbuf.length) {
                        cbuf = _textBuffer.expandCurrentSegment();
                    }
                    cbuf[cix++] = (char) (0xD800 + (ch >> 10));
                    ch = 0xDC00 | (ch & 0x03FF);
                }
            }
            if (cix >= cbuf.length) {
                cbuf = _textBuffer.expandCurrentSegment();
            }
            cbuf[cix++] = (char) ch;
        }

        // Ok. Now we have the character array, and can construct the String
        String baseName = new String(cbuf, 0, cix);
        // And finally, un-align if necessary
        if (lastQuadBytes < 4) {
            quads[quadLen-1] = lastQuad;
        }
        return _symbols.addName(baseName, quads, quadLen);
    }

    private final void _handleLongFieldName() throws IOException
    {
        // First: gather quads we need, looking for end marker
        final byte[] inBuf = _inputBuffer;
        int quads = 0;
        int bytes = 0;
        int q = 0;

        while (true) {
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            byte b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 0;
                break;
            }
            q = ((int) b) & 0xFF;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 1;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 2;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 3;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (quads >= _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, _quadBuffer.length + 256); // grow by 1k
            }
            _quadBuffer[quads++] = q;
        }
        // and if we have more bytes, append those too
        int byteLen = (quads << 2);
        if (bytes > 0) {
            if (quads >= _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, _quadBuffer.length + 256);
            }
            _quadBuffer[quads++] = q;
            byteLen += bytes;
        }
        
        // Know this name already?
        String name = _symbols.findName(_quadBuffer, quads);
        if (name == null) {
            name = _decodeLongUnicodeName(_quadBuffer, byteLen, quads);
        }
        if (_seenNames != null) {
           if (_seenNameCount >= _seenNames.length) {
               _seenNames = _expandSeenNames(_seenNames);
           }
           _seenNames[_seenNameCount++] = name;
        }
        _parsingContext.setCurrentName(name);
    }
    
    /**
     * Helper method for trying to find specified encoded UTF-8 byte sequence
     * from symbol table; if successful avoids actual decoding to String
     */
    private final String _findDecodedFromSymbols(int len) throws IOException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        // First: maybe we already have this name decoded?
        if (len < 5) {
	    int inPtr = _inputPtr;
	    final byte[] inBuf = _inputBuffer;
	    int q = inBuf[inPtr] & 0xFF;
	    if (--len > 0) {
	        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
	        if (--len > 0) {
	            q = (q << 8) + (inBuf[++inPtr] & 0xFF);
	            if (--len > 0) {
	                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
	            }
	        }
	    }
	    _quad1 = q;
	    return _symbols.findName(q);
        }
        if (len < 9) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            // First quadbyte is easy
            int q1 = (inBuf[inPtr] & 0xFF) << 8;
            q1 += (inBuf[++inPtr] & 0xFF);
            q1 <<= 8;
            q1 += (inBuf[++inPtr] & 0xFF);
            q1 <<= 8;
            q1 += (inBuf[++inPtr] & 0xFF);
            int q2 = (inBuf[++inPtr] & 0xFF);
            len -= 5;
            if (len > 0) {
                q2 = (q2 << 8) + (inBuf[++inPtr] & 0xFF);
                if (--len > 0) {
                    q2 = (q2 << 8) + (inBuf[++inPtr] & 0xFF);
                    if (--len > 0) {
                        q2 = (q2 << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            return _symbols.findName(q1, q2);
        }
        return _findDecodedMedium(len);
    }

    /**
     * Method for locating names longer than 8 bytes (in UTF-8)
     */
    private final String _findDecodedMedium(int len)
        throws IOException
    {
    	// first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, bufLen);
            }
        }
        // then decode, full quads first
        int offset = 0;
        int inPtr = _inputPtr;
        final byte[] inBuf = _inputBuffer;
        do {
            int q = (inBuf[inPtr++] & 0xFF) << 8;
            q |= inBuf[inPtr++] & 0xFF;
            q <<= 8;
            q |= inBuf[inPtr++] & 0xFF;
            q <<= 8;
            q |= inBuf[inPtr++] & 0xFF;
            _quadBuffer[offset++] = q;
        } while ((len -= 4) > 3);
        // and then leftovers
        if (len > 0) {
            int q = inBuf[inPtr] & 0xFF;
            if (--len > 0) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (--len > 0) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                }
            }
            _quadBuffer[offset++] = q;
        }
        return _symbols.findName(_quadBuffer, offset);
    }
    
    private static int[] _growArrayTo(int[] arr, int minSize) {
        final int size = minSize+4;
        if (arr == null) {
            return new int[size];
        }
        return Arrays.copyOf(arr, size);
    }

    /*
    /**********************************************************************
    /* Internal methods, secondary parsing
    /**********************************************************************
     */

    @Override
    protected void _parseNumericValue(int expType) throws IOException
    {
        if (_tokenIncomplete) {
            _reportError("No current token available, can not call accessors");
        }
    }
    
    /**
     * Method called to finish parsing of a token, given partial decoded
     * state.
     */
    protected final JsonToken _finishToken() throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            return JsonToken.NOT_AVAILABLE;
        }
        byte b = _inputBuffer[_inputPtr++];

        // first need to handle possible header, since that is usually not
        // exposed as an event (expect when it implies document boundary)
        if (_majorState == MAJOR_INITIAL) { // just need to see if we see something like header:
            if (b == SmileConstants.HEADER_BYTE_1) {
                if (!_handleHeader(0)) {
                    return JsonToken.NOT_AVAILABLE;
                }
                // if handled, get next byte to code (if available)
                if (_inputPtr >= _inputEnd) {
                    return JsonToken.NOT_AVAILABLE;
                }
                b = _inputBuffer[_inputPtr++];
            } else {
                // nope, not header marker.
                // header mandatory? not good...
                if (_cfgRequireHeader) {
                    String msg;
                    if (b == '{' || b == '[') {
                        msg = "Input does not start with Smile format header (first byte = 0x"
                            +Integer.toHexString(b & 0xFF)+") -- rather, it starts with '"+((char) b)
                            +"' (plain JSON input?) -- can not parse";
                    } else {
                        msg = "Input does not start with Smile format header (first byte = 0x"
                        +Integer.toHexString(b & 0xFF)+") and parser has REQUIRE_HEADER enabled: can not parse";
                    }
                    throw new JsonParseException(this, msg);
                }
            }
            // otherwise, fall through, with byte (_handleHeader has set _majorState)
        } else if (_majorState == MAJOR_HEADER) { // in-stream header
            if (!_handleHeader(_minorState)) {
                return JsonToken.NOT_AVAILABLE;
            }
            // is it enough to leave '_tokenIncomplete' false here?
            if (_inputPtr >= _inputEnd) {
                return JsonToken.NOT_AVAILABLE;
            }
            b = _inputBuffer[_inputPtr++];
            // fall through
        }

        switch (_majorState) {
        case MAJOR_VALUE_NUMBER_INT:
            return _nextInt(_minorState, _pendingInt);
        case MAJOR_VALUE_NUMBER_LONG:
            return _nextLong(_minorState, _pendingLong);
        case MAJOR_VALUE_NUMBER_BIGINT:
            return _nextBigInt(_minorState);
        case MAJOR_VALUE_NUMBER_FLOAT:
            return _nextFloat(_minorState, _pendingInt) ;
        case MAJOR_VALUE_NUMBER_DOUBLE:
            return _nextDouble(_minorState, _pendingLong);
        case MAJOR_VALUE_NUMBER_BIGDEC:
            return _nextBigDecimal(_minorState);
        }
        _throwInvalidState("Illegal state when trying to complete token: ");
        return null;
    }

    /*
    /**********************************************************************
    /* Internal methods, UTF8 decoding
    /**********************************************************************
     */

    /*
    private final int _decodeUtf8_2(int c)
        throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private final int _decodeUtf8_3(int c1)
        throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private final int _decodeUtf8_3fast(int c1)
        throws IOException
    {
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    // @return Character value <b>minus 0x10000</c>; this so that caller
    //    can readily expand it to actual surrogates
    private final int _decodeUtf8_4(int c)
        throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);

        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }

        // note: won't change it to negative here, since caller
        // already knows it'll need a surrogate
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }
*/
    
    /*
    /**********************************************************************
    /* Internal methods, error reporting
    /**********************************************************************
     */

    // !!! TODO: remove
    protected final void _loadMoreGuaranteed() throws IOException {
        _throwInternal();
    }

    protected void _reportInvalidSharedName(int index) throws IOException
    {
        if (_seenNames == null) {
            _reportError("Encountered shared name reference, even though document header explicitly declared no shared name references are included");
        }
       _reportError("Invalid shared name reference "+index+"; only got "+_seenNameCount+" names in buffer (invalid content)");
    }

    protected void _reportInvalidSharedStringValue(int index) throws IOException
    {
        if (_seenStringValues == null) {
            _reportError("Encountered shared text value reference, even though document header did not declared shared text value references may be included");
        }
       _reportError("Invalid shared text value reference "+index+"; only got "+_seenStringValueCount+" names in buffer (invalid content)");
    }
    
    protected void _reportInvalidChar(int c) throws JsonParseException
    {
        // Either invalid WS or illegal UTF-8 start char
        if (c < ' ') {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }
	
    protected void _reportInvalidInitial(int mask)
        throws JsonParseException
    {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask)
        throws JsonParseException
    {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask, int ptr)
        throws JsonParseException
    {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    protected void _throwInvalidState(String desc)
    {
        throw new IllegalStateException(desc+": majorState="+_majorState);
    }
}
