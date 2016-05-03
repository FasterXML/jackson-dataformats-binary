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

public class NonBlockingParserImpl
    extends ParserBase
    implements NonBlockingParser, NonBlockingInputFeeder
{
    private final static byte[] NO_BYTES = new byte[0];
    private final static int[] NO_INTS = new int[0];
    private final static String[] NO_STRINGS = new String[0];

    /*
    /**********************************************************************
    /* State constants
    /**********************************************************************
     */

    // // // Initial bootstrapping states:
    
    /**
     * State right after parser has been constructed: waiting for header
     * (which may or may not be mandatory).
     */
    protected final static int STATE_INITIAL = 0;


    /**
     * State for recognized header marker, either in-feed or initial.
     */
    protected final static int STATE_HEADER = 1;
    
    /**
     * State in which we are right after decoding a full token.
     */
    protected final static int STATE_TOKEN_COMPLETE = 2;
    
    // // // States for decoding numbers:
    protected final static int STATE_NUMBER_INT = 10;
    protected final static int STATE_NUMBER_LONG = 11;
    protected final static int STATE_NUMBER_BIGINT = 12;
    protected final static int STATE_NUMBER_FLOAT = 13;
    protected final static int STATE_NUMBER_DOUBLE = 14;
    protected final static int STATE_NUMBER_BIGDEC = 15;

    protected final static int STATE_SHORT_ASCII = 20;
    protected final static int STATE_SHORT_UNICODE = 21;
    protected final static int STATE_LONG_ASCII = 22;
    protected final static int STATE_LONG_UNICODE = 23;
    protected final static int STATE_LONG_SHARED = 24;
    protected final static int STATE_RAW_BINARY = 25;
    protected final static int STATE_QUOTED_BINARY = 26;
    
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */
    
    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;

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
    /* Input source config
    /**********************************************************************
     */
    
    /**
     * This buffer is actually provided via {@link NonBlockingInputFeeder}
     */
    protected byte[] _inputBuffer = NO_BYTES;
    
    /**
     * In addition to current buffer pointer, and end pointer,
     * we will also need to know number of bytes originally
     * contained. This is needed to correctly update location
     * information when the block has been completed.
     */
    protected int _origBufferLen;

    // And from ParserBase:
//    protected int _inputPtr;
//    protected int _inputEnd;
    
    /*
    /**********************************************************************
    /* Additional parsing state
    /**********************************************************************
     */

    /**
     * Current main decoding state
     */
    protected int _state;

    /**
     * Addition indicator within state; contextually relevant for just that state
     */
    protected int _substate;
    
    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete;

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

    public NonBlockingParserImpl(IOContext ctxt, int parserFeatures, int smileFeatures,
            ObjectCodec codec, ByteQuadsCanonicalizer sym)
    {
        super(ctxt, parserFeatures);        
        _objectCodec = codec;
        _symbols = sym;
        
        _tokenInputRow = -1;
        _tokenInputCol = -1;
        _smileBufferRecycler = _smileBufferRecycler();

        _currToken = JsonToken.NOT_AVAILABLE;
        _state = STATE_INITIAL;
        _tokenIncomplete = true;

        _cfgRequireHeader = (smileFeatures & SmileParser.Feature.REQUIRE_HEADER.getMask()) != 0;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    /**
     * Helper method called when it looks like input might contain the signature;
     * and it is necessary to detect and handle signature to get configuration
     * information it might have.
     * 
     * @return True if valid signature was found and handled; false if not
     */
    protected boolean handleSignature(boolean consumeFirstByte, boolean throwException)
        throws IOException, JsonParseException
    {
        if (consumeFirstByte) {
            ++_inputPtr;
        }
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        if (_inputBuffer[_inputPtr] != SmileConstants.HEADER_BYTE_2) {
            if (throwException) {
            	_reportError("Malformed content: signature not valid, starts with 0x3a but followed by 0x"
            			+Integer.toHexString(_inputBuffer[_inputPtr])+", not 0x29");
            }
            return false;
        }
        if (++_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();        	
        }
        if (_inputBuffer[_inputPtr] != SmileConstants.HEADER_BYTE_3) {
            if (throwException) {
            	_reportError("Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x"
            			+Integer.toHexString(_inputBuffer[_inputPtr])+", not 0xA");
            }
            return false;
        }
    	// Good enough; just need version info from 4th byte...
        if (++_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();        	
        }
        int ch = _inputBuffer[_inputPtr++];
        int versionBits = (ch >> 4) & 0x0F;
        // but failure with version number is fatal, can not ignore
        if (versionBits != SmileConstants.HEADER_VERSION_0) {
            _reportError("Header version number bits (0x"+Integer.toHexString(versionBits)+") indicate unrecognized version; only 0x0 handled by parser");
        }

        // can avoid tracking names, if explicitly disabled
        if ((ch & SmileConstants.HEADER_BIT_HAS_SHARED_NAMES) == 0) {
            _seenNames = null;
            _seenNameCount = -1;
        }
        // conversely, shared string values must be explicitly enabled
        if ((ch & SmileConstants.HEADER_BIT_HAS_SHARED_STRING_VALUES) != 0) {
            _seenStringValues = NO_STRINGS;
            _seenStringValueCount = 0;
        }
        _mayContainRawBinary = ((ch & SmileConstants.HEADER_BIT_HAS_RAW_BINARY) != 0);
        return true;
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
    public int releaseBuffered(OutputStream out) throws IOException
    {
        int count = _inputEnd - _inputPtr;
        if (count < 1) {
            return 0;
        }
        // let's just advance ptr to end
        int origPtr = _inputPtr;
        out.write(_inputBuffer, origPtr, count);
        return count;
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
    
    @Override
    protected final boolean loadMore() throws IOException {
        _throwInternal();
        return false;
    }
    
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
    protected void _finishString() throws IOException, JsonParseException {
        // should never be called; but must be defined for superclass
        _throwInternal();
    }

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
                /* 28-Jun-2011, tatu: With 1.9, caller needs to clear the buffer;
                 *   but we only need to clear up to count as it is not a hash area
                 */
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
                /* 28-Jun-2011, tatu: With 1.9, caller needs to clear the buffer;
                 *   but we only need to clear up to count as it is not a hash area
                 */
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

    @Override
    public JsonToken nextToken() throws IOException, JsonParseException
    {
        _numTypesValid = NR_UNKNOWN;
        // have we already decoded part of event? If so, continue...
        if (_tokenIncomplete) {
            // we might be able to optimize by separate skipping, but for now:
            return _finishToken();
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;
        // Two main modes: values, and field names.
        if (_parsingContext.inObject() && _currToken != JsonToken.FIELD_NAME) {
            return (_currToken = _handleFieldName());
        }
        if (_inputPtr >= _inputEnd) {
            return JsonToken.NOT_AVAILABLE;
        }
        int ch = _inputBuffer[_inputPtr++];
        switch ((ch >> 5) & 0x7) {
        case 0: // short shared string value reference
            if (ch == 0) { // important: this is invalid, don't accept
                _reportError("Invalid token byte 0x00");
            }
            return _handleSharedString(ch-1);

        case 1: // simple literals, numbers
            {
                _numTypesValid = 0;
                switch (ch & 0x1F) {
                case 0x00:
                    _textBuffer.resetWithEmpty();
                    return (_currToken = JsonToken.VALUE_STRING);
                case 0x01:
                    return (_currToken = JsonToken.VALUE_NULL);
                case 0x02: // false
                    return (_currToken = JsonToken.VALUE_FALSE);
                case 0x03: // 0x03 == true
                    return (_currToken = JsonToken.VALUE_TRUE);
                case 0x04:
                    _state = STATE_NUMBER_INT;
                    return _nextInt(0, 0);
                case 0x05:
                    _numberLong = 0;
                    _state = STATE_NUMBER_LONG;
                    return _nextLong(0, 0L);
                case 0x06:
                    _state = STATE_NUMBER_BIGINT;
                    return _nextBigInt(0);
                case 0x07: // illegal
                    break;
                case 0x08:
                    _pendingInt = 0;
                    _state = STATE_NUMBER_FLOAT;
                    _got32BitFloat = true;
                    return _nextFloat(0, 0);
                case 0x09:
                    _pendingLong = 0L;
                    _state = STATE_NUMBER_DOUBLE;
                    _got32BitFloat = false;
                    return _nextDouble(0, 0L);
                case 0x0A:
                    _state = STATE_NUMBER_BIGDEC;
                    return _nextBigDecimal(0);
                case 0x0B: // illegal
                    break;
                case 0x1A: // == 0x3A == ':' -> possibly header signature for next chunk?
                    if (!_handleHeader(0)) {
                        return JsonToken.NOT_AVAILABLE;
                    }
                    //if (handleSignature(false, false)) {
                    /* Ok, now; end-marker and header both imply doc boundary and a
                     * 'null token'; but if both are seen, they are collapsed.
                     * We can check this by looking at current token; if it's null,
                     * need to get non-null token
                     */
                    if (_currToken == null) {
                        return nextToken();
                    }
                    return (_currToken = null);
                }
            }
            // and everything else is reserved, for now
            break;
        case 2: // tiny ASCII
            // fall through            
        case 3: // short ASCII
            // fall through
            return _nextShortAscii(0);

        case 4: // tiny Unicode
            // fall through
        case 5: // short Unicode
            // No need to decode, unless we have to keep track of back-references (for shared string values)
            _currToken = JsonToken.VALUE_STRING;
            if (_seenStringValueCount >= 0) { // shared text values enabled
                _addSeenStringValue();
            } else {
                _tokenIncomplete = true;
            }
            return _nextShortUnicode(0);

        case 6: // small integers; zigzag encoded
            _numberInt = SmileUtil.zigzagDecode(ch & 0x1F);
            _numTypesValid = NR_INT;
            return (_currToken = JsonToken.VALUE_NUMBER_INT);
        case 7: // binary/long-text/long-shared/start-end-markers
            switch (ch & 0x1F) {
            case 0x00: // long variable length ASCII
                return _nextLongAscii(0);
            case 0x04: // long variable length unicode
                return _nextLongUnicode(0);
            case 0x08: // binary, 7-bit
                return _nextQuotedBinary(0);
            case 0x0C: // long shared string
            case 0x0D:
            case 0x0E:
            case 0x0F:
                return _nextLongSharedString(0);
//                return _handleSharedString(((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF));
            case 0x18: // START_ARRAY
                _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
                return (_currToken = JsonToken.START_ARRAY);
            case 0x19: // END_ARRAY
                if (!_parsingContext.inArray()) {
                    _reportMismatchedEndMarker(']', '}');
                }
                _parsingContext = _parsingContext.getParent();
                return (_currToken = JsonToken.END_ARRAY);
            case 0x1A: // START_OBJECT
                _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
                return (_currToken = JsonToken.START_OBJECT);
            case 0x1B: // not used in this mode; would be END_OBJECT
                _reportError("Invalid type marker byte 0xFB in value mode (would be END_OBJECT in key mode)");
            case 0x1D: // binary, raw
                // should we validate this is legal? (as per header)
                return _nextRawBinary(0);
            case 0x1F: // 0xFF, end of content
                return (_currToken = null);
            }
            break;
        }
        // If we get this far, type byte is corrupt
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch & 0xFF)+" for expected value token");
        return null;
    }

    private final JsonToken _handleSharedString(int index)
        throws IOException, JsonParseException
    {
        if (index >= _seenStringValueCount) {
            _reportInvalidSharedStringValue(index);
        }
        _textBuffer.resetWithString(_seenStringValues[index]);
        return (_currToken = JsonToken.VALUE_STRING);
    }

    private final void _addSeenStringValue()
        throws IOException, JsonParseException
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
    public String getCurrentName() throws IOException, JsonParseException
    {
        return _parsingContext.getCurrentName();
    }

    @Override
    public NumberType getNumberType()
        throws IOException, JsonParseException
    {
    	if (_got32BitFloat) {
    	    return NumberType.FLOAT;
    	}
    	return super.getNumberType();
    }

    /*
    /**********************************************************************
    /* AsyncInputFeeder impl
    /**********************************************************************
     */

    @Override
    public final boolean needMoreInput() {
        return (_inputPtr >=_inputEnd) && !_endOfInput;
    }

    @Override
    public void feedInput(byte[] buf, int start, int len)
        throws IOException
    {
        // Must not have remaining input
        if (_inputPtr < _inputEnd) {
            throw new IOException("Still have "+(_inputEnd - _inputPtr)+" undecoded bytes, should not call 'feedInput'");
        }
        // and shouldn't have been marked as end-of-input
        if (_endOfInput) {
            throw new IOException("Already closed, can not feed more input");
        }
        // Time to update pointers first
        _currInputProcessed += _origBufferLen;
        _currInputRowStart -= _origBufferLen;

        // And then update buffer settings
        _inputBuffer = buf;
        _inputPtr = start;
        _inputEnd = start+len;
        _origBufferLen = len;
    }

    @Override
    public void endOfInput() {
        _endOfInput = true;
    }
    
    /*
    /**********************************************************************
    /* NonBlockParser impl (except for NonBlockingInputFeeder)
    /**********************************************************************
     */

    @Override
    public JsonToken peekNextToken() throws IOException, JsonParseException
    {
        if (!_tokenIncomplete) {
            return JsonToken.NOT_AVAILABLE;
        }
        switch (_state) {
        case STATE_INITIAL: // the case if no input has yet been fed
            return JsonToken.NOT_AVAILABLE;
        case STATE_HEADER:
            return JsonToken.NOT_AVAILABLE;
        case STATE_NUMBER_INT:
        case STATE_NUMBER_LONG:
            return JsonToken.VALUE_NUMBER_INT;
        case STATE_NUMBER_FLOAT:
        case STATE_NUMBER_DOUBLE:
        case STATE_NUMBER_BIGDEC:
            return JsonToken.VALUE_NUMBER_FLOAT;
        }
        throw new IllegalStateException("Internal error: unknown 'state', "+_state);
    }
    
    /*
    /**********************************************************************
    /* Internal methods: second-level parsing:
    /**********************************************************************
     */

    private final JsonToken _nextInt(int substate, int value)
        throws IOException, JsonParseException
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
        _substate = substate;
        _pendingInt = value;
        _state = STATE_NUMBER_INT;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _nextLong(int substate, long value) throws IOException, JsonParseException
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
        _substate = substate;
        _pendingLong = value;
        _state = STATE_NUMBER_LONG;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _nextBigInt(int substate) throws IOException, JsonParseException
    {
        // !!! TBI
        _tokenIncomplete = true;
        _substate = substate;
//        _pendingLong = value;
        _state = STATE_NUMBER_BIGDEC;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    private final boolean _finishBigInteger()
        throws IOException, JsonParseException
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
    
    private final JsonToken _nextFloat(int substate, int value) throws IOException, JsonParseException
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
        _substate = substate;
        _pendingInt = value;
        _state = STATE_NUMBER_FLOAT;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _nextDouble(int substate, long value) throws IOException, JsonParseException
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
        _substate = substate;
        _pendingLong = value;
        _state = STATE_NUMBER_DOUBLE;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _nextBigDecimal(int substate) throws IOException, JsonParseException
    {
        // !!! TBI
        _tokenIncomplete = true;
        _substate = substate;
//        _pendingLong = value;
        _state = STATE_NUMBER_BIGDEC;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
/*
    private final void _finishBigDecimal()
        throws IOException, JsonParseException
    {
        int scale = SmileUtil.zigzagDecode(_readUnsignedVInt());
        byte[] raw = _read7BitBinaryWithLength();
        _numberBigDecimal = new BigDecimal(new BigInteger(raw), scale);
        _numTypesValid = NR_BIGDECIMAL;
    }
    
 */

    /*
    private final int _readUnsignedVInt()
        throws IOException, JsonParseException
    {
        int value = 0;
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
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
    
    private final boolean _handleHeader(int substate) throws IOException, JsonParseException
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
        _state = STATE_HEADER;
        _substate = substate;
        return false;
    }

    private final JsonToken _nextShortAscii(int substate) throws IOException, JsonParseException
    {
        _state = STATE_SHORT_ASCII;
        _tokenIncomplete = true;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _nextShortUnicode(int substate) throws IOException, JsonParseException
    {
        _state = STATE_SHORT_UNICODE;
        _tokenIncomplete = true;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    /*
    protected final void _decodeShortAsciiValue(int len)
        throws IOException, JsonParseException
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
        throws IOException, JsonParseException
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
    
    private final JsonToken _nextLongAscii(int substate) throws IOException, JsonParseException
    {
        // did not get it all; mark the state so we know where to return:
        _state = STATE_LONG_ASCII;
        _tokenIncomplete = true;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    private final void _decodeLongAscii()
        throws IOException, JsonParseException
    {
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        main_loop:
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
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

    private final JsonToken _nextLongUnicode(int substate) throws IOException, JsonParseException
    {
        // did not get it all; mark the state so we know where to return:
        _state = STATE_LONG_UNICODE;
        _tokenIncomplete = true;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    
/*
    private final void _decodeLongUnicode()
        throws IOException, JsonParseException
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
                    loadMoreGuaranteed();
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
    
    private final JsonToken _nextLongSharedString(int substate) throws IOException, JsonParseException
    {
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _state = STATE_LONG_SHARED;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _nextRawBinary(int substate) throws IOException, JsonParseException
    {
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _state = STATE_RAW_BINARY;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

/*

    private final void _finishRawBinary()
        throws IOException, JsonParseException
    {
        int byteLen = _readUnsignedVInt();
        _binaryValue = new byte[byteLen];
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
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
            loadMoreGuaranteed();
        }
    }
 */
    
    private final JsonToken _nextQuotedBinary(int substate) throws IOException, JsonParseException
    {
        // did not get it all; mark the state so we know where to return:
        _tokenIncomplete = true;
        _state = STATE_QUOTED_BINARY;
        _substate = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    /*
    private final byte[] _read7BitBinaryWithLength()
        throws IOException, JsonParseException
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
    
//    public boolean nextFieldName(SerializableString str) throws IOException, JsonParseException
//    public String nextTextValue() throws IOException, JsonParseException
//    public int nextIntValue(int defaultValue) throws IOException, JsonParseException
//    public long nextLongValue(long defaultValue) throws IOException, JsonParseException
//    public Boolean nextBooleanValue() throws IOException, JsonParseException
    
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
    public String getText()
        throws IOException, JsonParseException
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
    public char[] getTextCharacters()
        throws IOException, JsonParseException
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
        throws IOException, JsonParseException
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
    public int getTextOffset() throws IOException, JsonParseException
    {
        return 0;
    }

    /*
    /**********************************************************************
    /* Public API, access to token information, binary
    /**********************************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant)
        throws IOException, JsonParseException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            // Todo, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject()
        throws IOException, JsonParseException
    {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    // could possibly implement this... or maybe not.
    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out)
            throws IOException, JsonParseException {
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
    protected final JsonToken _handleFieldName() throws IOException, JsonParseException
    {    	
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
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
                        loadMoreGuaranteed();
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
        throws IOException, JsonParseException
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
                    _reportInvalidEOF(" in long field name");
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

    private final void _handleLongFieldName() throws IOException, JsonParseException
    {
        // First: gather quads we need, looking for end marker
        final byte[] inBuf = _inputBuffer;
        int quads = 0;
        int bytes = 0;
        int q = 0;

        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            byte b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 0;
                break;
            }
            q = ((int) b) & 0xFF;
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 1;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (BYTE_MARKER_END_OF_STRING == b) {
                bytes = 2;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
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
        throws IOException, JsonParseException
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
    protected final JsonToken _finishToken()
    	throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            return JsonToken.NOT_AVAILABLE;
        }
        byte b = _inputBuffer[_inputPtr++];

        // first need to handle possible header, since that is usually not
        // exposed as an event (expect when it implies document boundary)
        if (_state == STATE_INITIAL) { // just need to see if we see something like header:
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
            // otherwise, fall through, with byte (_handleHeader has set _state)
        } else if (_state == STATE_HEADER) { // in-stream header
            if (!_handleHeader(_substate)) {
                return JsonToken.NOT_AVAILABLE;
            }
            // is it enough to leave '_tokenIncomplete' false here?
            if (_inputPtr >= _inputEnd) {
                return JsonToken.NOT_AVAILABLE;
            }
            b = _inputBuffer[_inputPtr++];
            // fall through
        }

        switch (_state) {
        case STATE_NUMBER_INT:
            return _nextInt(_substate, _pendingInt);
        case STATE_NUMBER_LONG:
            return _nextLong(_substate, _pendingLong);
        case STATE_NUMBER_BIGINT:
            return _nextBigInt(_substate);
        case STATE_NUMBER_FLOAT:
            return _nextFloat(_substate, _pendingInt) ;
        case STATE_NUMBER_DOUBLE:
            return _nextDouble(_substate, _pendingLong);
        case STATE_NUMBER_BIGDEC:
            return _nextBigDecimal(_substate);
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
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private final int _decodeUtf8_3(int c1)
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private final int _decodeUtf8_3fast(int c1)
        throws IOException, JsonParseException
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
        throws IOException, JsonParseException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);

        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
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
        throw new IllegalStateException(desc+": state="+_state);
    }
}
