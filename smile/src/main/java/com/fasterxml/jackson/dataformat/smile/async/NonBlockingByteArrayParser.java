package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.async.NonBlockingInputFeeder;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.VersionUtil;

import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.dataformat.smile.SmileUtil;

import static com.fasterxml.jackson.dataformat.smile.SmileConstants.BYTE_MARKER_END_OF_STRING;

public class NonBlockingByteArrayParser
    extends NonBlockingParserBase
    implements ByteArrayFeeder
{
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
    /* Life-cycle
    /**********************************************************************
     */

    public NonBlockingByteArrayParser(IOContext ctxt, int parserFeatures, int smileFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(ctxt, parserFeatures, smileFeatures, sym);
    }

    /*
    /**********************************************************************
    /* AsyncInputFeeder impl
    /**********************************************************************
     */

    @Override
    public ByteArrayFeeder getNonBlockingInputFeeder() {
        return this;
    }

    @Override
    public final boolean needMoreInput() {
        return (_inputPtr >=_inputEnd) && !_endOfInput;
    }

    @Override
    public void feedInput(byte[] buf, int start, int end) throws IOException
    {
        // Must not have remaining input
        if (_inputPtr < _inputEnd) {
            _reportError("Still have %d undecoded bytes, should not call 'feedInput'", _inputEnd - _inputPtr);
        }
        if (end < start) {
            _reportError("Input end (%d) may not be before start (%d)", end, start);
        }
        // and shouldn't have been marked as end-of-input
        if (_endOfInput) {
            _reportError("Already closed, can not feed more input");
        }
        // Time to update pointers first
        _currInputProcessed += _origBufferLen;

        // And then update buffer settings
        _inputBuffer = buf;
        _inputPtr = start;
        _inputEnd = end;
        _origBufferLen = end - start;
    }

    @Override
    public void endOfInput() {
        _endOfInput = true;
    }

    /*
    /**********************************************************************
    /* Abstract methods/overrides from JsonParser
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

    @Override
    public int releaseBuffered(OutputStream out) throws IOException {
        int avail = _inputEnd - _inputPtr;
        if (avail > 0) {
            out.write(_inputBuffer, _inputPtr, avail);
        }
        return avail;
    }

    /*
    /**********************************************************************
    /* Main-level decoding
    /**********************************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException
    {
        // First: regardless of where we really are, need at least one more byte;
        // can simplify some of the checks by short-circuiting right away
        if (_inputPtr >= _inputEnd) {
            if (_closed) {
                return null;
            }
            // note: if so, do not even bother changing state
            if (_endOfInput) { // except for this special case
                return _eofAsNextToken();
            }
            return JsonToken.NOT_AVAILABLE;
        }
        // in the middle of tokenization?
        if (_currToken == JsonToken.NOT_AVAILABLE) {
            return _finishToken();
        }

        // No: fresh new token; may or may not have existing one
        _numTypesValid = NR_UNKNOWN;
//            _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;
        int ch = _inputBuffer[_inputPtr++];

        switch (_majorState) {
        case MAJOR_INITIAL:
            if (SmileConstants.HEADER_BYTE_1 == ch) { // yes, initial header; should be good
                // minor state as 0, which is fine
                _majorState = MAJOR_ROOT;
                _minorState = MINOR_HEADER_INITIAL;
                return _finishHeader(0);
            }
            if (SmileParser.Feature.REQUIRE_HEADER.enabledIn(_formatFeatures)) {
                _reportMissingHeader(ch);
            }
            // otherwise fine, just drop through to next state
            // (NOTE: it double-checks header; fine, won't match; just need the rest)
            _majorState = MAJOR_ROOT;
            return _startValue(ch);

        case MAJOR_ROOT: // 
            if (SmileConstants.HEADER_BYTE_1 == ch) { // looks like a header
                _minorState = MINOR_HEADER_INLINE;
                return _finishHeader(0);
            }
            return _startValue(ch);

        case MAJOR_OBJECT_FIELD: // field or end-object
            // expect name
            return _startFieldName(ch);
            
        case MAJOR_OBJECT_VALUE:
        case MAJOR_ARRAY_ELEMENT: // element or end-array
            return _startValue(ch);

        default:
        }
        VersionUtil.throwInternal();
        return null;
    }

    /**
     * Method called when a (scalar) value type has been detected, but not all of
     * contents have been decoded due to incomplete input available.
     */
    protected final JsonToken _finishToken() throws IOException
    {
        // NOTE: caller ensures availability of at least one byte

        switch (_minorState) {
        case MINOR_HEADER_INITIAL:
        case MINOR_HEADER_INLINE:
            return _finishHeader(_pending32);

        case MINOR_FIELD_NAME_2BYTE:
            return _handleSharedName(_pending32 + (_inputBuffer[_inputPtr++] & 0xFF));

        case MINOR_FIELD_NAME_LONG:
            return _finishLongFieldName(_inputCopyLen);

        case MINOR_FIELD_NAME_SHORT_ASCII:
        case MINOR_FIELD_NAME_SHORT_UNICODE:
            {
                final int fullLen = _pending32;
                final int needed = fullLen - _inputCopyLen;
                final int avail = _inputEnd - _inputPtr;
                if (avail >= needed) { // got it all
                    System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, _inputCopyLen, needed);
                    _inputPtr += needed;
                    String name = _findDecodedFromSymbols(_inputCopy, 0, fullLen);
                    if (name == null) {
                        name = (_minorState == MINOR_FIELD_NAME_SHORT_ASCII)
                                ? _decodeASCIIText(_inputCopy, 0, fullLen)
                                : _decodeShortUnicodeText(_inputCopy, 0, fullLen)
                                ;
                        name = _addDecodedToSymbols(fullLen, name);
                    }
                    // either way, may need to keep a copy for possible back-ref
                    if (_seenNames != null) {
                        if (_seenNameCount >= _seenNames.length) {
                            _seenNames = _expandSeenNames(_seenNames);
                        }
                        _seenNames[_seenNameCount++] = name;
                    }
                    _parsingContext.setCurrentName(name);
                    _majorState = MAJOR_OBJECT_VALUE;
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // Otherwise append to buffer, not done
                System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, _inputCopyLen, avail);
                _inputPtr += avail;
                _inputCopyLen += avail;
            }
            return JsonToken.NOT_AVAILABLE;

        case MINOR_VALUE_NUMBER_INT:
            return _finishInt(_pending32, _inputCopyLen);
        case MINOR_VALUE_NUMBER_LONG:
            return _finishLong(_pending64, _inputCopyLen);

        case MINOR_VALUE_NUMBER_BIGINT_LEN:
            return _finishBigIntLen(_pending32, _inputCopyLen);
        case MINOR_VALUE_NUMBER_BIGINT_BODY:
            return _finishBigIntBody();

        case MINOR_VALUE_NUMBER_FLOAT:
            return _finishFloat(_pending32, _inputCopyLen);
        case MINOR_VALUE_NUMBER_DOUBLE:
            return _finishDouble(_pending64, _inputCopyLen);

        case MINOR_VALUE_NUMBER_BIGDEC_SCALE:
            return _finishBigDecimalScale((int) _pending64, _inputCopyLen);
        case MINOR_VALUE_NUMBER_BIGDEC_LEN:
            return _finishBigDecimalLen(_pending32, _inputCopyLen);
        case MINOR_VALUE_NUMBER_BIGDEC_BODY:
            return _finishBigDecimalBody();
            
        case MINOR_VALUE_STRING_SHORT_ASCII:
        case MINOR_VALUE_STRING_SHORT_UNICODE:
            {
                final int fullLen = _pending32;
                final int needed = fullLen - _inputCopyLen;
                final int avail = _inputEnd - _inputPtr;
                if (avail >= needed) { // got it all
                    System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, _inputCopyLen, needed);
                    _inputPtr += needed;
                    String text = (_minorState == MINOR_FIELD_NAME_SHORT_ASCII)
                            ? _decodeASCIIText(_inputCopy, 0, fullLen)
                            : _decodeShortUnicodeText(_inputCopy, 0, fullLen);
                    if (_seenStringValueCount >= 0) { // shared text values enabled
                        _addSeenStringValue(text);
                    }
                    return _valueComplete(JsonToken.VALUE_STRING);
                }
                // Otherwise append to buffer, not done
                System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, _inputCopyLen, avail);
                _inputPtr += avail;
                _inputCopyLen += avail;
            }
            return JsonToken.NOT_AVAILABLE;
        
        case MINOR_VALUE_STRING_LONG_ASCII:
            return _finishLongASCII();

        case MINOR_VALUE_STRING_LONG_UNICODE:
            return _finishLongUnicode();

        case MINOR_VALUE_STRING_SHARED_2BYTE:
            return _handleSharedString(_pending32 + (_inputBuffer[_inputPtr++] & 0xFF));

        case MINOR_VALUE_BINARY_RAW_LEN:
            return _finishRawBinaryLen(_pending32, _inputCopyLen);
        case MINOR_VALUE_BINARY_RAW_BODY:
            return _finishRawBinaryBody();

        case MINOR_VALUE_BINARY_7BIT_LEN:
            return _finish7BitBinaryLen(_pending32, _inputCopyLen);
        case MINOR_VALUE_BINARY_7BIT_BODY:
            return _finish7BitBinaryBody();
        default:
        }
        throw new IllegalStateException("Illegal state when trying to complete token: majorState="+_majorState);
    }

    /*
    /**********************************************************************
    /* Second-level decoding
    /**********************************************************************
     */

    /**
     * Helper method that will decode information from a header block that has been
     * detected.
     */
    protected JsonToken _finishHeader(int state) throws IOException
    {
        int ch = 0;
        String errorDesc = null;
        
        switch (state) {
        case 0:
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch!= SmileConstants.HEADER_BYTE_2) {
                errorDesc = "Malformed content: signature not valid, starts with 0x3a but followed by 0x%s, not 0x29";
                break;
            }
            state = 1;
            // fall through
        case 1:
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch != SmileConstants.HEADER_BYTE_3) {
                errorDesc = "Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x%s not 0x0A";
                break;
            }
            state = 2;
        case 2:
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            {
                int versionBits = (ch >> 4) & 0x0F;
                // but failure with version number is fatal, can not ignore
                if (versionBits != SmileConstants.HEADER_VERSION_0) {
                    _reportError("Header version number bits (0x%s) indicate unrecognized version; only 0x0 handled by parser",
                            Integer.toHexString(versionBits));
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
            }
            _majorState = MAJOR_ROOT;
            _currToken = null;

            // Mild difference here: initial marker not reported separately, but in-line
            // ones need to be reported as `null` tokens as they are logical document end
            // markers (although should be collated with actual end markers)
            if (_minorState == MINOR_HEADER_INLINE) {
                return null;
            }
            // Ok to use recursion in case of initial header, as well:
            return nextToken();
        default:
        }
        _reportError(errorDesc, Integer.toHexString(ch));
        return null;
    }

    /**
     * Helper method called to detect type of a value token (at any level), and possibly
     * decode it if contained in input buffer.
     * Note that possible header has been ruled out by caller and is not checked here.
     */
    private final JsonToken _startValue(int ch) throws IOException
    {
        main_switch:
        switch ((ch >> 5) & 0x7) {
        case 0: // short shared string value reference
            if (ch == 0) { // important: this is invalid, don't accept
                _reportError("Invalid token byte 0x00");
            }
            return _handleSharedString(ch-1);

        case 1: // simple literals, numbers
            _numTypesValid = 0;
            switch (ch & 0x1F) {
            case 0x00:
                _textBuffer.resetWithEmpty();
                return _valueComplete(JsonToken.VALUE_STRING);
            case 0x01:
                return _valueComplete(JsonToken.VALUE_NULL);
            case 0x02: // false
                return _valueComplete(JsonToken.VALUE_FALSE);
            case 0x03: // 0x03 == true
                return _valueComplete(JsonToken.VALUE_TRUE);
            case 0x04:
                return _startInt();
            case 0x05:
                return _startLong();
            case 0x06:
                return _startBigInt();
            case 0x07: // illegal
                break;
            case 0x08:
                return _startFloat();
            case 0x09:
                return _startDouble();
            case 0x0A:
                return _startBigDecimal();
            case 0x0B: // illegal
                break;
            case 0x1A:
                // == 0x3A == ':' -> possibly switch; but should be handled elsewhere so...
                break main_switch;
            }
            // and everything else is reserved, for now
            break;
        case 2: // tiny ASCII
            // fall through            
        case 3: // short ASCII
            // fall through
            return _startShortASCII(1 + (ch & 0x3F));

        case 4: // tiny Unicode
            // fall through
        case 5: // short Unicode
            return _startShortUnicode(2 + (ch & 0x3F));

        case 6: // small integers; zigzag encoded
            _numberInt = SmileUtil.zigzagDecode(ch & 0x1F);
            _numTypesValid = NR_INT;
            _numberType = NumberType.INT;
            return _valueComplete(JsonToken.VALUE_NUMBER_INT);
        case 7: // binary/long-text/long-shared/start-end-markers
            switch (ch & 0x1F) {
            case 0x00: // long variable length ASCII
                return _startLongASCII();
            case 0x04: // long variable length unicode
                return _startLongUnicode();
            case 0x08: // binary, 7-bit
                return _start7BitBinary();
            case 0x0C: // long shared string
            case 0x0D:
            case 0x0E:
            case 0x0F:
                {
                    ch = (ch & 0x3) << 8;
                    if (_inputPtr < _inputEnd) {
                        return _handleSharedString(ch + (_inputBuffer[_inputPtr++] & 0xFF));
                    }
                }
                // did not get it all; mark the state so we know where to return:
                _pending32 = ch;
                _minorState = MINOR_VALUE_STRING_SHARED_2BYTE;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            case 0x18: // START_ARRAY
                return _startArrayScope();
            case 0x19: // END_ARRAY
                return _closeArrayScope();
            case 0x1A: // START_OBJECT
                return _startObjectScope();
            case 0x1B: // not used in this mode; would be END_OBJECT
                _reportError("Invalid type marker byte 0xFB in value mode (would be END_OBJECT in key mode)");
            case 0x1D: // binary, raw
                // should we validate this is legal? (as per header)
                return _startRawBinary();
            case 0x1F: // 0xFF, end of content
                return (_currToken = null);
            }
            break;
        }
        // If we get this far, type byte is corrupt
        _reportError("Invalid type marker byte 0x%02x for expected value token", ch & 0xFF);
        return null;
    }

    /*
    /**********************************************************************
    /* Second-level decoding, Name decoding
    /**********************************************************************
     */

    /**
     * Method that handles initial token type recognition for token
     * that has to be either FIELD_NAME or END_OBJECT.
     */
    protected final JsonToken _startFieldName(int ch) throws IOException
    {
        switch ((ch >> 6) & 3) {
        case 0: // misc, including end marker
            switch (ch) {
            case 0x20: // empty String as name, legal if unusual
                _parsingContext.setCurrentName("");
                _majorState = MAJOR_OBJECT_VALUE;
                return (_currToken = JsonToken.FIELD_NAME);
            case 0x30: // long shared
            case 0x31:
            case 0x32:
            case 0x33:
                if (_inputPtr < _inputEnd) {
                    return _handleSharedName(((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF));
                }
                {
                    _minorState = MINOR_FIELD_NAME_2BYTE;
                    _pending32 = (ch & 0x3) << 8;
                    return (_currToken = JsonToken.NOT_AVAILABLE);
                }
            case 0x34: // long ASCII/Unicode name
                return _finishLongFieldName(0);
            }
            break;
        case 1: // short shared, can fully process
            return _handleSharedName(ch & 0x3F);
        case 2: // short ASCII; possibly doable
            {
                final int len = 1 + (ch & 0x3f);
                final int inputPtr = _inputPtr;
                final int left = _inputEnd - inputPtr;
                if (len <= left) { // gotcha!
                    _inputPtr = inputPtr + len;
                    String name = _findDecodedFromSymbols(_inputBuffer, inputPtr, len);
                    if (name == null) {
                        name = _decodeASCIIText(_inputBuffer, inputPtr, len);
                        name = _addDecodedToSymbols(len, name);
                    }
                    // either way, may need to keep a copy for possible back-ref
                    if (_seenNames != null) {
                        if (_seenNameCount >= _seenNames.length) {
                            _seenNames = _expandSeenNames(_seenNames);
                        }
                        _seenNames[_seenNameCount++] = name;
                    }
                    _parsingContext.setCurrentName(name);
                    _majorState = MAJOR_OBJECT_VALUE;
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // Nope: need to copy
                _pending32 = len;
                _inputCopyLen = left;
                if (left > 0) {
                    _inputPtr = inputPtr + left;
                    System.arraycopy(_inputBuffer, inputPtr, _inputCopy, 0, left);
                }
            }
            _minorState = MINOR_FIELD_NAME_SHORT_ASCII;
            return (_currToken = JsonToken.NOT_AVAILABLE);

        case 3: // short Unicode; possibly doable
            // all valid, except for 0xFF
            ch &= 0x3F;
            {
                if (ch > 0x37) {
                    if (ch == 0x3B) {
                        return _closeObjectScope();
                    }
                    // error, but let's not worry about that here
                    break;
                }
                final int len = ch + 2; // values from 2 to 57...
                final int inputPtr = _inputPtr;
                final int left = _inputEnd - inputPtr;
                if (len <= left) { // gotcha!
                    _inputPtr = inputPtr + len;
                    String name = _findDecodedFromSymbols(_inputBuffer, inputPtr, len);
                    if (name == null) {
                        name = _decodeShortUnicodeText(_inputBuffer, inputPtr, len);
                        name = _addDecodedToSymbols(len, name);
                    }
                    if (_seenNames != null) {
                        if (_seenNameCount >= _seenNames.length) {
                         _seenNames = _expandSeenNames(_seenNames);
                        }
                        _seenNames[_seenNameCount++] = name;
                    }
                    _parsingContext.setCurrentName(name);
                    _majorState = MAJOR_OBJECT_VALUE;
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // Nope: need to copy
                _pending32 = len;
                _inputCopyLen = left;
                if (left > 0) {
                    _inputPtr = inputPtr + left;
                    System.arraycopy(_inputBuffer, inputPtr, _inputCopy, 0, left);
                }
                _minorState = MINOR_FIELD_NAME_SHORT_UNICODE;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
        }
        // Other byte values are illegal
        _reportError("Invalid type marker byte 0x%02x for expected field name (or END_OBJECT marker)", ch & 0xFF);
        return null;
    }

    private final JsonToken _finishLongFieldName(int outPtr) throws IOException
    {
        byte[] srcBuffer = _inputBuffer;
        byte[] copyBuffer = _inputCopy;
        int srcPtr = _inputPtr;

        copy_loop:
        while (true) {
            int max = Math.min(_inputEnd - srcPtr, copyBuffer.length - outPtr);
            final int inputEnd = srcPtr + max;

            while (srcPtr < inputEnd) {
                byte b = srcBuffer[srcPtr++];
                if (b == BYTE_MARKER_END_OF_STRING) {
                    break copy_loop;
                }
                copyBuffer[outPtr++] = b;
            }
            // If end of input, bail out
            if (srcPtr == _inputEnd) {
                _inputPtr = srcPtr;
                _minorState = MINOR_FIELD_NAME_LONG;
                _inputCopyLen = outPtr;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            // otherwise increase copy buffer length
            int oldLen = copyBuffer.length;
            int incr = Math.min(64000, oldLen >> 1);
            _inputCopy = copyBuffer = Arrays.copyOf(_inputCopy, oldLen + incr);
            // and loop again
        }

        // But if we get here, we got it all, only need to create quads etc
        _inputPtr = srcPtr;
        int[] quads = _quadBuffer;
        int qlen = (outPtr + 3) >> 2; // last quad may be partial

        if (quads.length < qlen) {
            _quadBuffer = quads = Arrays.copyOf(quads, qlen + 16);
        }
        int in = 0;
        int quadCount = 0;

        for (final int inEnd = (outPtr & ~3); in < inEnd; in += 4) {
            int q = (copyBuffer[in] << 24)
                    | ((copyBuffer[in+1] & 0xFF) << 16)
                    | ((copyBuffer[in+2] & 0xFF) << 8)
                    | (copyBuffer[in+3] & 0xFF);
            quads[quadCount++] = q;
        }
        // and possibly more... ?
        if (in < outPtr) { // at least 1
            int q = copyBuffer[in++] & 0xFF;
            if (in < outPtr) { // at least 2
                q = (q << 8) | (copyBuffer[in++] & 0xFF);
                if (in < outPtr) { // 3 (can't be more)
                    q = (q << 8) | (copyBuffer[in++] & 0xFF);
                }
            }
            quads[quadCount++] = q;
        }
        
        String name = _symbols.findName(quads, quadCount);
        if (name == null) {
            name = _decodeLongUnicodeName(copyBuffer, 0, outPtr);
        }
        if (_seenNames != null) {
           if (_seenNameCount >= _seenNames.length) {
               _seenNames = _expandSeenNames(_seenNames);
           }
           _seenNames[_seenNameCount++] = name;
        }
        _parsingContext.setCurrentName(name);
        _majorState = MAJOR_OBJECT_VALUE;
        return (_currToken = JsonToken.FIELD_NAME);
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Strings, short (length-prefix)
    /**********************************************************************
     */

    private final JsonToken _startShortASCII(final int len) throws IOException
    {
        final int inputPtr = _inputPtr;
        final int left = _inputEnd - inputPtr;
        if (len <= left) { // gotcha!
            _inputPtr = inputPtr + len;
            String text = _decodeASCIIText(_inputBuffer, inputPtr, len);
            if (_seenStringValueCount >= 0) { // shared text values enabled
                _addSeenStringValue(text);
            }
            return _valueComplete(JsonToken.VALUE_STRING);
        }
        // Nope: need to copy
        _pending32 = len;
        _inputCopyLen = left;
        if (left > 0) {
            _inputPtr = inputPtr + left;
            System.arraycopy(_inputBuffer, inputPtr, _inputCopy, 0, left);
        }
        _minorState = MINOR_VALUE_STRING_SHORT_ASCII;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _startShortUnicode(final int len) throws IOException
    {
        final int inPtr = _inputPtr;
        final int left = _inputEnd - inPtr;
        if (len <= left) { // gotcha!
            _inputPtr = inPtr + len;
            String text = _decodeShortUnicodeText(_inputBuffer, inPtr, len);
            if (_seenStringValueCount >= 0) { // shared text values enabled
                _addSeenStringValue(text);
            }
            return _valueComplete(JsonToken.VALUE_STRING);
        }
        // Nope: need to copy
        _pending32 = len;
        _inputCopyLen = left;
        if (left > 0) {
            System.arraycopy(_inputBuffer, inPtr, _inputCopy, 0, left);
            _inputPtr = inPtr + left;
        }
        _minorState = MINOR_VALUE_STRING_SHORT_UNICODE;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Strings, long (end marker)
    /**********************************************************************
     */

    private final JsonToken _startLongASCII() throws IOException
    {
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();

        while (_inputPtr < _inputEnd) {
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
                    _textBuffer.setCurrentLength(outPtr);
                    return _valueComplete(JsonToken.VALUE_STRING);
                }
                outBuf[outPtr++] = (char) b;                    
            } while (--left > 0);
            _inputPtr = inPtr;
        }
        // denote current length; no partial input to save
        _textBuffer.setCurrentLength(outPtr);
        _minorState = MINOR_VALUE_STRING_LONG_ASCII;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finishLongASCII() throws IOException
    {
        char[] outBuf = _textBuffer.getBufferWithoutReset();
        int outPtr = _textBuffer.getCurrentSegmentSize();

        while (_inputPtr < _inputEnd) {
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
                    _textBuffer.setCurrentLength(outPtr);
                    return _valueComplete(JsonToken.VALUE_STRING);
                }
                outBuf[outPtr++] = (char) b;                    
            } while (--left > 0);
            _inputPtr = inPtr;
        }
        // denote current length; no partial input to save
        _textBuffer.setCurrentLength(outPtr);
        return JsonToken.NOT_AVAILABLE;
    }

    protected final JsonToken _startLongUnicode() throws IOException
    {
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        int c;
        final byte[] inputBuffer = _inputBuffer;

        // NOTE: caller guarantees there is at least one byte available at this point!

        main_loop:
        while (true) {
            // First the tight ASCII loop:
            ascii_loop:
            while (true) {
                int ptr = _inputPtr;
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
                    c = inputBuffer[ptr++] & 0xFF;
                    if (codes[c] != 0) {
                        _inputPtr = ptr;
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (char) c;
                }
                _inputPtr = ptr;
                if (ptr >= _inputEnd) {
                    _inputCopyLen = 0;
                    break main_loop;
                }
            }
            // Ok: end marker, escape or multi-byte?
            if (c == SmileConstants.INT_MARKER_END_OF_STRING) {
                _textBuffer.setCurrentLength(outPtr);
                return _valueComplete(JsonToken.VALUE_STRING);
            }

            // otherwise need at least one more byte, so:
            if (_inputPtr >= _inputEnd) {
                _pending32 = c;
                _inputCopyLen = 1;
                break main_loop;
            }
            int d = _inputBuffer[_inputPtr++];

            switch (codes[c]) {
            case 1: // 2-byte UTF
                c = _decodeUTF8_2(c, d);
                break;
            case 2: // 3-byte UTF
                if (_inputPtr >= _inputEnd) {
                    _pending32 = c;
                    _inputCopy[0] = (byte) d;
                    _inputCopyLen = 2;
                    break main_loop;
                }
                c = _decodeUTF8_3(c, d, _inputBuffer[_inputPtr++]);
                break;
            case 3: // 4-byte UTF
                if ((_inputPtr + 1) >= _inputEnd) {
                    _pending32 = c;
                    _inputCopy[0] = (byte) d;
                    if (_inputPtr >= _inputEnd) {
                        _inputCopyLen = 2;
                    } else {
                        _inputCopy[1] = _inputBuffer[_inputPtr++];
                        _inputCopyLen = 3;
                    }
                    break main_loop;
                }
                c = _decodeUTF8_4(c, d, _inputBuffer[_inputPtr++], _inputBuffer[_inputPtr++]);
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
                _reportInvalidInitial(c);
            }
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
            if (_inputPtr >= _inputEnd) {
                _inputCopyLen = 0; // no partially decoded UTF-8 codepoint
                break;
            }
        }
        _textBuffer.setCurrentLength(outPtr);
        _minorState = MINOR_VALUE_STRING_LONG_UNICODE;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finishLongUnicode() throws IOException
    {
        // First things first: did we have partially decoded multi-byte UTF-8 character?
        if (_inputCopyLen > 0) {
            if (!_finishPartialUnicodeChar()) {
                return JsonToken.NOT_AVAILABLE;
            }
        }

        final int[] codes = SmileConstants.sUtf8UnitLengths;
        int c;
        final byte[] inputBuffer = _inputBuffer;
        char[] outBuf = _textBuffer.getBufferWithoutReset();
        int outPtr = _textBuffer.getCurrentSegmentSize();

        main_loop:
        while (true) {
            // First the tight ASCII loop:
            ascii_loop:
            while (true) {
                int ptr = _inputPtr;
                // Since we have no guarantee for any content, check it first
                if (ptr >= _inputEnd) {
                    _inputCopyLen = 0; // no partially decoded UTF-8 codepoint
                    break main_loop;
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
                    c = inputBuffer[ptr++] & 0xFF;
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
                _textBuffer.setCurrentLength(outPtr);
                return _valueComplete(JsonToken.VALUE_STRING);
            }

            // otherwise need at least one more byte, so:
            if (_inputPtr >= _inputEnd) {
                _pending32 = c;
                _inputCopyLen = 1;
                break main_loop;
            }
            int d = _inputBuffer[_inputPtr++];

            switch (codes[c]) {
            case 1: // 2-byte UTF
                c = _decodeUTF8_2(c, d);
                break;
            case 2: // 3-byte UTF
                if (_inputPtr >= _inputEnd) {
                    _pending32 = c;
                    _inputCopy[0] = (byte) d;
                    _inputCopyLen = 2;
                    break main_loop;
                }
                c = _decodeUTF8_3(c, d, _inputBuffer[_inputPtr++]);
                break;
            case 3: // 4-byte UTF
                if ((_inputPtr + 1) >= _inputEnd) {
                    _pending32 = c;
                    _inputCopy[0] = (byte) d;
                    if (_inputPtr >= _inputEnd) {
                        _inputCopyLen = 2;
                    } else {
                        _inputCopy[1] = _inputBuffer[_inputPtr++];
                        _inputCopyLen = 3;
                    }
                    break main_loop;
                }
                c = _decodeUTF8_4(c, d, _inputBuffer[_inputPtr++], _inputBuffer[_inputPtr++]);
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
                _reportInvalidInitial(c);
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
        return JsonToken.NOT_AVAILABLE;
    }

    private final boolean _finishPartialUnicodeChar() throws IOException
    {
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        int c;

        // NOTE: first byte stored in `_pending32` and we know we got one more byte for sure
        int next = _inputBuffer[_inputPtr++];
        switch (codes[_pending32]) { // type of UTF-8 sequence (length - 1)
        case 1: // 2-byte UTF
            c = _decodeUTF8_2(_pending32, next);
            break;
        case 2: // 3-byte UTF: did we have one or two bytes?
            if (_inputCopyLen == 1) {
                if (_inputPtr >= _inputEnd) {
                    _inputCopy[0] = (byte) next;
                    _inputCopyLen = 2;
                    return false;
                }
                c = _decodeUTF8_3(_pending32, next, _inputBuffer[_inputPtr++]);
            } else {
                c = _decodeUTF8_3(_pending32, _inputCopy[0], next);
            }
            break;
        case 3: // 4-byte UTF; had 1/2/3 bytes, now got 2/3/4
            switch (_inputCopyLen) {
            case 1:
                if (_inputPtr >= _inputEnd) {
                    _inputCopy[0] = (byte) next;
                    _inputCopyLen = 2;
                    return false;
                }
                int i3 = _inputBuffer[_inputPtr++];
                if (_inputPtr >= _inputEnd) {
                    _inputCopy[0] = (byte) next;
                    _inputCopy[1] = (byte) i3;
                    _inputCopyLen = 3;
                    return false;
                }
                c = _decodeUTF8_4(_pending32, next, i3, _inputBuffer[_inputPtr++]);
                break;
            case 2:
                if (_inputPtr >= _inputEnd) {
                    _inputCopy[1] = (byte) next;
                    _inputCopyLen = 3;
                    return false;
                }
                c = _decodeUTF8_4(_pending32, _inputCopy[0], next, _inputBuffer[_inputPtr++]);
                break;
            case 3:
            default:
                c = _decodeUTF8_4(_pending32, _inputCopy[0], _inputCopy[1], next);
                break;
            }
            // Let's add first part right away:
            _textBuffer.append((char) (0xD800 | (c >> 10)));
            c = 0xDC00 | (c & 0x3FF);
            // And let the other char output down below
            break;
        default:
            // Is this good enough error message?
            _reportInvalidInitial(_pending32);
            c = 0;
        }
        _inputCopyLen = 0; // just for safety
        _textBuffer.append((char) c);
        return true;
    }

    /*
    /**********************************************************************
    /* Internal methods, UTF8 decoding
    /**********************************************************************
     */

    private final int _decodeUTF8_2(int c, int d) throws IOException
    {
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private final int _decodeUTF8_3(int c, int d, int e) throws IOException
    {
        c &= 0x0F;
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        if ((e & 0xC0) != 0x080) {
            _reportInvalidOther(e & 0xFF, _inputPtr);
        }
        return (c << 6) | (e & 0x3F);
    }

    // @return Character value <b>minus 0x10000</c>; this so that caller
    //    can readily expand it to actual surrogates
    private final int _decodeUTF8_4(int c, int d, int e, int f) throws IOException
    {
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        if ((e & 0xC0) != 0x080) {
            _reportInvalidOther(e & 0xFF, _inputPtr);
        }
        c = (c << 6) | (e & 0x3F);
        if ((f & 0xC0) != 0x080) {
            _reportInvalidOther(f & 0xFF, _inputPtr);
        }
        return ((c << 6) | (f & 0x3F)) - 0x10000;
    }
    
    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: numbers, integral
    /**********************************************************************
     */

    private final JsonToken _startInt() throws IOException
    {
        // common case first: have all we need
        if ((_inputPtr + 5) > _inputEnd) {
            return _finishInt(0, 0);
        }
        int value = _decodeVInt();
        _numberInt = SmileUtil.zigzagDecode(value);
        _numTypesValid = NR_INT;
        _numberType = NumberType.INT;
        return _valueComplete(JsonToken.VALUE_NUMBER_INT);
    }

    private final JsonToken _finishInt(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                value = (value << 6) | (b & 0x3F);
                _numberInt = SmileUtil.zigzagDecode(value);
                _numTypesValid = NR_INT;
                _numberType = NumberType.INT;
                return _valueComplete(JsonToken.VALUE_NUMBER_INT);
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_NUMBER_INT;
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _startLong() throws IOException
    {
        // common case first: have all we need
        int ptr = _inputPtr;
        final int maxEnd = ptr+11;
        if (maxEnd >= _inputEnd) {
            return _finishLong(0L, 0);
        }
        int i = _inputBuffer[ptr++]; // first 7 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 14 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 21
        i = (i << 7) + _inputBuffer[ptr++];

        long l = i;
        while (true) {
            int value = _inputBuffer[ptr++];
            if (value < 0) {
                l = (l << 6) + (value & 0x3F);
                _inputPtr = ptr;
                _numberLong = SmileUtil.zigzagDecode(l);
                _numTypesValid = NR_LONG;
                _numberType = NumberType.LONG;
                return _valueComplete(JsonToken.VALUE_NUMBER_INT);
            }
            l = (l << 7) + value;
            if (ptr >= maxEnd) {
                _reportError("Corrupt input; 64-bit VInt extends beyond 11 data bytes");
            }
        }
    }

    private final JsonToken _finishLong(long value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                value = (value << 6) | (b & 0x3F);
                _numberLong = SmileUtil.zigzagDecode(value);
                _numTypesValid = NR_LONG;
                _numberType = NumberType.LONG;
                return _valueComplete(JsonToken.VALUE_NUMBER_INT);
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 11) {
                _reportError("Corrupt input; 64-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_NUMBER_LONG;
        _pending64 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _startBigInt() throws IOException
    {
        _initByteArrayBuilder();
        if ((_inputPtr + 5) > _inputEnd) {
            return _finishBigIntLen(0, 0);
        }
        _pending32 = _decodeVInt();
        _inputCopyLen = 0;
        return _finishBigIntBody();
    }

    private final JsonToken _finishBigIntLen(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                _pending32 = (value << 6) | (b & 0x3F);
                _inputCopyLen = 0;
                return _finishBigIntBody();
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_NUMBER_BIGINT_LEN;
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finishBigIntBody() throws IOException
    {
        if (_decode7BitEncoded()) { // got it all!
            _numberBigInt = new BigInteger(_byteArrayBuilder.toByteArray());
            _numberType = NumberType.BIG_INTEGER;
            _numTypesValid = NR_BIGINT;
            return _valueComplete(JsonToken.VALUE_NUMBER_INT);
        }
        _minorState = MINOR_VALUE_NUMBER_BIGINT_BODY;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: numbers, floating-point
    /**********************************************************************
     */

    protected final JsonToken _startFloat() throws IOException
    {
        int ptr = _inputPtr;
        if ((ptr + 5) > _inputEnd) {
            return _finishFloat(0, 0);
        }
        // NOTE! all bytes guaranteed to be unsigned (should verify?)
        int i = _fourBytesToInt(ptr);
        ptr += 4;
        i = (i << 7) + _inputBuffer[ptr++];
        _inputPtr = ptr;
        _numberFloat = (float) Float.intBitsToFloat(i);
        _numTypesValid = NR_FLOAT;
        _numberType = NumberType.FLOAT;
        return _valueComplete(JsonToken.VALUE_NUMBER_FLOAT);
    }

    protected final JsonToken _finishFloat(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            value = (value << 7) + _inputBuffer[_inputPtr++];
            if (++bytesRead == 5) {
                _numberFloat = (float) Float.intBitsToFloat(value);
                _numTypesValid = NR_FLOAT;
                _numberType = NumberType.FLOAT;
                return _valueComplete(JsonToken.VALUE_NUMBER_FLOAT);
            }
        }
        _minorState = MINOR_VALUE_NUMBER_FLOAT;
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    protected final JsonToken _startDouble() throws IOException
    {
        int ptr = _inputPtr;
        if ((ptr + 10) > _inputEnd) {
            return _finishDouble(0L, 0);
        }
        // NOTE! all bytes guaranteed to be unsigned (should verify?)
        long hi = _fourBytesToInt(ptr);
        ptr += 4;
        long value = (hi << 28) + (long) _fourBytesToInt(ptr);
        ptr += 4;

        // and then remaining 2 bytes
        value = (value << 7) + _inputBuffer[ptr++];
        value = (value << 7) + _inputBuffer[ptr++];
        _inputPtr = ptr;
        _numberDouble = Double.longBitsToDouble(value);
        _numTypesValid = NR_DOUBLE;
        _numberType = NumberType.DOUBLE;
        return _valueComplete(JsonToken.VALUE_NUMBER_FLOAT);
    }

    protected final JsonToken _finishDouble(long value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            value = (value << 7) + _inputBuffer[_inputPtr++];
            if (++bytesRead == 10) {
                _numberDouble = Double.longBitsToDouble(value);
                _numTypesValid = NR_DOUBLE;
                _numberType = NumberType.DOUBLE;
                return _valueComplete(JsonToken.VALUE_NUMBER_FLOAT);
            }
        }
        _minorState = MINOR_VALUE_NUMBER_DOUBLE;
        _pending64 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _startBigDecimal() throws IOException
    {
        _initByteArrayBuilder();
        if ((_inputPtr + 5) > _inputEnd) {
            return _finishBigDecimalScale(0, 0);
        }
        // note! Scale stored here, need _pending32 for byte length
        _pending64 = _decodeVInt();
        return _finishBigDecimalLen(0, 0);
    }

    private final JsonToken _finishBigDecimalScale(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                value = (value << 6) | (b & 0x3F);
                _pending64 = value;
                return _finishBigDecimalLen(0, 0);
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_NUMBER_BIGDEC_SCALE;
        // note! Scale stored here, need _pending32 for byte length
        _pending64 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finishBigDecimalLen(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                _pending32 = (value << 6) | (b & 0x3F);
                _inputCopyLen = 0;
                return _finishBigDecimalBody();
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_NUMBER_BIGDEC_LEN;
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finishBigDecimalBody() throws IOException
    {
        if (_decode7BitEncoded()) { // got it all!
            // note: scale value is signed, needs zigzag, so:
            int scale = SmileUtil.zigzagDecode((int) _pending64);
            BigInteger bigInt = new BigInteger(_byteArrayBuilder.toByteArray());
            _numberBigDecimal = new BigDecimal(bigInt, scale);
            _numberType = NumberType.BIG_DECIMAL;
            _numTypesValid = NR_BIGDECIMAL;
            return _valueComplete(JsonToken.VALUE_NUMBER_FLOAT);
        }
        _minorState = MINOR_VALUE_NUMBER_BIGDEC_BODY;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Binary
    /**********************************************************************
     */
    
    protected final JsonToken _startRawBinary() throws IOException
    {
        if ((_inputPtr + 5) > _inputEnd) {
            return _finishRawBinaryLen(0, 0);
        }
        final int len = _decodeVInt();
        _binaryValue = new byte[len];
        _pending32 = len;
        _inputCopyLen = 0;
        return _finishRawBinaryBody();
    }

    private final JsonToken _finishRawBinaryLen(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                final int len = (value << 6) | (b & 0x3F);
                _binaryValue = new byte[len];
                _pending32 = len;
                _inputCopyLen = 0;
                return _finishRawBinaryBody();
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_BINARY_RAW_LEN;
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finishRawBinaryBody() throws IOException
    {
        int totalLen = _pending32;
        int offset = _inputCopyLen;
        
        int needed = totalLen - offset;
        int avail = _inputEnd - _inputPtr;
        if (avail >= needed) {
            System.arraycopy(_inputBuffer, _inputPtr, _binaryValue, offset, needed);
            _inputPtr += needed;
            return _valueComplete(JsonToken.VALUE_EMBEDDED_OBJECT);
        }
        if (avail > 0) {
            System.arraycopy(_inputBuffer, _inputPtr, _binaryValue, offset, avail);
            _inputPtr += avail;
        }
        _pending32 = totalLen;
        _inputCopyLen = offset+avail;
        _minorState = MINOR_VALUE_BINARY_RAW_BODY;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    private final JsonToken _start7BitBinary() throws IOException
    {
        _initByteArrayBuilder();
        if ((_inputPtr + 5) > _inputEnd) {
            return _finish7BitBinaryLen(0, 0);
        }
        _pending32 = _decodeVInt();
        _inputCopyLen = 0;
        return _finish7BitBinaryBody();
    }

    private final JsonToken _finish7BitBinaryLen(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                _pending32 = (value << 6) | (b & 0x3F);
                _inputCopyLen = 0;
                return _finish7BitBinaryBody();
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        _minorState = MINOR_VALUE_BINARY_7BIT_LEN;
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _finish7BitBinaryBody() throws IOException
    {
        if (_decode7BitEncoded()) { // got it all!
            _binaryValue = _byteArrayBuilder.toByteArray();
            return _valueComplete(JsonToken.VALUE_EMBEDDED_OBJECT);
        }
        _minorState = MINOR_VALUE_BINARY_7BIT_BODY;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
    /*
    /**********************************************************************
    /* Shared text decoding methods
    /**********************************************************************
     */

    private final String _decodeASCIIText(byte[] inBuf, int inPtr, int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;

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
        _textBuffer.setCurrentLength(len);
        return _textBuffer.contentsAsString();
    }

    /**
     * Helper method used to decode short Unicode string, length for which actual
     * length (in bytes) is known
     * 
     * @param len Length between 1 and 64
     */
    private final String _decodeShortUnicodeText(byte[] inBuf, int inPtr, int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = SmileConstants.sUtf8UnitLengths;
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
                    _reportError("Invalid byte 0x%02x in short Unicode text block (offset %d)", i & 0xFF, inPtr);
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        _textBuffer.setCurrentLength(outPtr);
        return _textBuffer.contentsAsString();
    }

    private final String _decodeLongUnicodeName(byte[] inBuf, int inPtr, int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        // since we only check expansion for multi-byte chars, there must be
        // enough room for remaining bytes as all-ASCII
        int estSlack = outBuf.length - len - 8;

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
                    _reportError("Invalid byte 0x%02x in short Unicode text block (offset %d)", i & 0xFF, inPtr);
                }
                estSlack -= code;
                if (estSlack <= 0) {
                    outBuf = _textBuffer.expandCurrentSegment();
                    // and re-adjust: most likely we are now safe but...
                    estSlack = (outBuf.length - outPtr) - (end - inPtr) - 8;
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        _textBuffer.setCurrentLength(outPtr);
        return _textBuffer.contentsAsString();
    }
    
    /*
    /**********************************************************************
    /* Low-level decoding of building blocks (vints, 7-bit encoded blocks)
    /**********************************************************************
     */

    private final int _fourBytesToInt(int ptr)  throws IOException
    {
        int i = _inputBuffer[ptr++]; // first 7 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 14 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 21
        i = (i << 7) + _inputBuffer[ptr++];
        return i;
    }

    private final int _decodeVInt() throws IOException
    {
        int ptr = _inputPtr;
        int value = _inputBuffer[ptr++];
        if (value < 0) { // 6 bits
            _inputPtr = ptr;
            return value & 0x3F;
        }
        int i = _inputBuffer[ptr++];
        if (i >= 0) { // 13 bits
            value = (value << 7) + i;
            i = _inputBuffer[ptr++];
            if (i >= 0) {
                value = (value << 7) + i;
                i = _inputBuffer[ptr++];
                if (i >= 0) {
                    value = (value << 7) + i;
                    // and then we must get negative
                    i = _inputBuffer[ptr++];
                    if (i >= 0) {
                        _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
                    }
                }
            }
        }
        _inputPtr = ptr;
        return (value << 6) + (i & 0x3F);
    }

    private final boolean _decode7BitEncoded() throws IOException
    {
        int bytesToDecode = _pending32;
        int buffered = _inputCopyLen;
        
        int ptr = _inputPtr;
        int avail = _inputEnd - ptr;

        // Leftovers from past round?
        if (buffered > 0) {
            // but offline case of incomplete last block
            if (bytesToDecode < 7) {
                return _decode7BitEncodedTail(bytesToDecode, buffered);
            }
            int needed = 8 - buffered;
            if (avail < needed) { // not enough to decode, just copy
                System.arraycopy(_inputBuffer, ptr, _inputCopy, buffered, avail);
                _inputPtr = ptr+avail;
                _inputCopyLen = buffered + avail;
                _pending32 = bytesToDecode;
                return false;
            }
            _inputCopyLen = 0;
            // yes, got full 8 byte chunk
            final byte[] copy = _inputCopy;
            System.arraycopy(_inputBuffer, ptr, copy, buffered, needed);
            int i1 = (copy[0] << 25) + (copy[1] << 18)
                    + (copy[2] << 11) + (copy[3] << 4);
            int x = copy[4];
            i1 += x >> 3;
            _byteArrayBuilder.appendFourBytes(i1);
            i1 = ((x & 0x7) << 21) + (copy[5] << 14)
                + (copy[6] << 7) + copy[7];
            _byteArrayBuilder.appendThreeBytes(i1);
            ptr += needed;
            bytesToDecode -= 7;
            avail = _inputEnd - ptr;
        }

        final byte[] input = _inputBuffer;
        // And then all full 8-to-7-byte chunks
        while (bytesToDecode > 6) {
            if (avail < 8) { // full blocks missing, quit
                if (avail > 0) {
                    System.arraycopy(_inputBuffer, ptr, _inputCopy, 0, avail);
                    ptr += avail;
                    _inputCopyLen = avail;
                }
                _pending32 = bytesToDecode;
                _inputPtr = ptr;
                return false;
            }
            int i1 = (input[ptr++] << 25)
                + (input[ptr++] << 18)
                + (input[ptr++] << 11)
                + (input[ptr++] << 4);
            int x = input[ptr++];
            i1 += x >> 3;
            _byteArrayBuilder.appendFourBytes(i1);
            i1 = ((x & 0x7) << 21)
                + (input[ptr++] << 14)
                + (input[ptr++] << 7)
                + input[ptr++];
            _byteArrayBuilder.appendThreeBytes(i1);
            bytesToDecode -= 7;
            avail -= 8;
        }
        _inputPtr = ptr;
        // and finally, tail?
        if (bytesToDecode > 0) {
            if (avail == 0) {
                _pending32 = bytesToDecode;
                _inputCopyLen = 0;
                return false;
            }
            return _decode7BitEncodedTail(bytesToDecode, 0);
        }
        return true;
    }

    protected final boolean _decode7BitEncodedTail(int bytesToDecode, int buffered) throws IOException
    {
        if (bytesToDecode == 0) {
            return true;
        }
        int avail = _inputEnd - _inputPtr;
        int needed = bytesToDecode + 1 - buffered;

        if (avail < needed) {
            System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, buffered, avail);
            _inputPtr += avail;
            _inputCopyLen = buffered + avail;
            _pending32 = bytesToDecode;
            return false;
        }
        System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, buffered, needed);
        _inputPtr += needed;

        // Handling of full tail is bit different...
        int value = _inputCopy[0];
        for (int i = 1; i < bytesToDecode; ++i) {
            value = (value << 7) + _inputCopy[i];
            _byteArrayBuilder.append(value >> (7 - i));
        }
        // last byte is different, has remaining 1 - 6 bits, right-aligned
        value <<= bytesToDecode;
        _byteArrayBuilder.append(value + _inputCopy[bytesToDecode]);
        _inputCopyLen = 0;
        return true;
    }
}
