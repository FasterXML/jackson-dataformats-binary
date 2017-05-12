package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.VersionUtil;

import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.dataformat.smile.SmileUtil;

public class NonBlockingByteArrayParser
    extends NonBlockingParserBase<NonBlockingByteArrayFeeder>
    implements NonBlockingByteArrayFeeder
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
    public NonBlockingByteArrayFeeder getInputFeeder() {
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
            throw new IOException("Still have "+(_inputEnd - _inputPtr)+" undecoded bytes, should not call 'feedInput'");
        }
        if (end < start) {
            throw new IOException("Input end ("+end+") may not be before start ("+start+")");
        }
        // and shouldn't have been marked as end-of-input
        if (_endOfInput) {
            throw new IOException("Already closed, can not feed more input");
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
                _minorState = MINOR_HEADER;
                _pending32 = 0;
                return _finishHeader();
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
                _minorState = MINOR_HEADER;
                _pending32 = 0;
                return _finishHeader();
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
        case MINOR_HEADER:
            return _finishHeader();

        case MINOR_FIELD_NAME_2BYTE:
            {
                int index = _pending32 + (_inputBuffer[_inputPtr++] & 0xFF);
                if (index >= _seenNameCount) {
                    _reportInvalidSharedName(index);
                }
                _parsingContext.setCurrentName(_seenNames[index]);
            }
            _majorState = MAJOR_OBJECT_VALUE;
            return (_currToken = JsonToken.FIELD_NAME);

        case MINOR_FIELD_NAME_LONG:
            // !!! TBI!
            break;
        
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
                                ? _decodeShortASCII(_inputCopy, 0, fullLen)
                                : _decodeShortUnicode(_inputCopy, 0, fullLen)
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
            return _finishBigIntBody(_pending32, _inputCopyLen);

        case MINOR_VALUE_NUMBER_FLOAT:
            return _finishFloat(_pending32, _inputCopyLen);
        case MINOR_VALUE_NUMBER_DOUBLE:
            return _finishDouble(_pending64, _inputCopyLen);

        case MINOR_VALUE_NUMBER_BIGDEC_SCALE:
            return _finishBigDecimalScale((int) _pending64, _inputCopyLen);
        case MINOR_VALUE_NUMBER_BIGDEC_LEN:
            return _finishBigDecimalLen(_pending32, _inputCopyLen);
        case MINOR_VALUE_NUMBER_BIGDEC_BODY:
            return _finishBigDecimalBody(_pending32, _inputCopyLen);
            
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
                            ? _decodeShortASCII(_inputCopy, 0, fullLen)
                            : _decodeShortUnicode(_inputCopy, 0, fullLen);
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
        case MINOR_VALUE_BINARY_RAW_BODY:

        case MINOR_VALUE_BINARY_7BIT_LEN:
        case MINOR_VALUE_BINARY_7BIT_BODY:
            break;
        default:
        }
        _throwInvalidState("Illegal state when trying to complete token: ");
        return null;
    }

    /*
    /**********************************************************************
    /* Second-level decoding, root level
    /**********************************************************************
     */

    /**
     * Helper method that will decode information from a header block that has been
     * detected.
     */
    protected JsonToken _finishHeader() throws IOException
    {
        int ch;
        switch (_pending32) {
        case 0:
            if (_inputPtr >= _inputEnd) {
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch!= SmileConstants.HEADER_BYTE_2) {
                _reportError("Malformed content: signature not valid, starts with 0x3a but followed by 0x"
                        +Integer.toHexString(ch)+", not 0x29");
            }
            _pending32 = 1;
            // fall through
        case 1:
            if (_inputPtr >= _inputEnd) {
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch != SmileConstants.HEADER_BYTE_3) {
                _reportError("Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x"
                        +Integer.toHexString(ch)+", not 0xA");
            }
            _pending32 = 2;
        case 2:
            if (_inputPtr >= _inputEnd) {
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            {
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
            }
            break;
        default:
            VersionUtil.throwInternal();
        }
        _majorState = MAJOR_ROOT;
        _currToken = null;
        // Ok to use recursion; not very often used, very unlikely to have a sequence of
        // headers (although allowed pointless)
        return nextToken();
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
                return _startQuotedBinary(0);
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
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch & 0xFF)+" for expected value token");
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
                if (_inputPtr >= _inputEnd) {
                    _minorState = MINOR_FIELD_NAME_2BYTE;
                    _pending32 = (ch & 0x3) << 8;
                    return (_currToken = JsonToken.NOT_AVAILABLE);
                }
                {
                    int index = ((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
                    if (index >= _seenNameCount) {
                        _reportInvalidSharedName(index);
                    }
                    _parsingContext.setCurrentName(_seenNames[index]);
                }
                _majorState = MAJOR_OBJECT_VALUE;
                return (_currToken = JsonToken.FIELD_NAME);
            case 0x34: // long ASCII/Unicode name
                _minorState = MINOR_FIELD_NAME_LONG;

                // !!! TODO: Implement !!!
VersionUtil.throwInternal();

                return (_currToken = JsonToken.FIELD_NAME);
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
            _majorState = MAJOR_OBJECT_VALUE;
            return (_currToken = JsonToken.FIELD_NAME);

        case 2: // short ASCII; possibly doable
            {
                final int len = 1 + (ch & 0x3f);
                final int left = _inputEnd - _inputPtr;
                if (len <= left) { // gotcha!
                    String name = _findDecodedFromSymbols(_inputBuffer, _inputPtr, len);
                    if (name == null) {
                        name = _decodeShortASCII(_inputBuffer, _inputPtr, len);
                        name = _addDecodedToSymbols(len, name);
                    }
                    _inputPtr += len;
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
                    System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
                    _inputPtr += left;
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
                } else {
                    final int len = ch + 2; // values from 2 to 57...
                    final int left = _inputEnd - _inputPtr;
                    if (len <= left) { // gotcha!
                        String name = _findDecodedFromSymbols(_inputBuffer, _inputPtr, len);
                        if (name == null) {
                            name = _decodeShortUnicode(_inputBuffer, _inputPtr, len);
                            name = _addDecodedToSymbols(len, name);
                        }
                        _inputPtr += len;
                        if (_seenNames != null) {
                            if (_seenNameCount >= _seenNames.length) {
                             _seenNames = _expandSeenNames(_seenNames);
                            }
                            _seenNames[_seenNameCount++] = name;
                        }
                        return (_currToken = JsonToken.FIELD_NAME);
                    }
                    // Nope: need to copy
                    _pending32 = len;
                    _inputCopyLen = left;
                    if (left > 0) {
                        System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
                    }
                }
                _minorState = MINOR_FIELD_NAME_SHORT_UNICODE;
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
        }
        // Other byte values are illegal
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch)
                +" for expected field name (or END_OBJECT marker)");
        return null;
    }

    /*
    private final String _decodeLongUnicodeName(int[] quads, int byteLen, int quadLen) throws IOException
    {
        int lastQuadBytes = byteLen & 3;

        // Ok: must decode UTF-8 chars. No other validation SHOULD be needed (except bounds checks?)
        // Note: last quad is not correctly aligned (leading zero bytes instead
        // need to shift a bit, instead of trailing). Only need to shift it
        // for UTF-8 decoding; need revert for storage (since key will not
        // be aligned, to optimize lookup speed)

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
    */

    /*
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
            if (SmileConstants.BYTE_MARKER_END_OF_STRING == b) {
                bytes = 0;
                break;
            }
            q = ((int) b) & 0xFF;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (SmileConstants.BYTE_MARKER_END_OF_STRING == b) {
                bytes = 1;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (SmileConstants.BYTE_MARKER_END_OF_STRING == b) {
                bytes = 2;
                break;
            }
            q = (q << 8) | (b & 0xFF);
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            b = inBuf[_inputPtr++];
            if (SmileConstants.BYTE_MARKER_END_OF_STRING == b) {
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
    */


    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Strings, short (length-prefix)
    /**********************************************************************
     */

    private final JsonToken _startShortASCII(final int len) throws IOException
    {
        final int left = _inputEnd - _inputPtr;
        if (len <= left) { // gotcha!
            String text = _decodeShortASCII(len);
            if (_seenStringValueCount >= 0) { // shared text values enabled
                _addSeenStringValue(text);
            }
            return _valueComplete(JsonToken.VALUE_STRING);
        }
        // Nope: need to copy
        _pending32 = len;
        _inputCopyLen = left;
        if (left > 0) {
            System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
            _inputPtr += left;
        }
        _minorState = MINOR_VALUE_STRING_SHORT_ASCII;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    private final JsonToken _startShortUnicode(final int len) throws IOException
    {
        final int left = _inputEnd - _inputPtr;
        if (len <= left) { // gotcha!
            String text = _decodeShortUnicode(len);
            if (_seenStringValueCount >= 0) { // shared text values enabled
                _addSeenStringValue(text);
            }
            return _valueComplete(JsonToken.VALUE_STRING);
        }
        // Nope: need to copy
        _pending32 = len;
        _inputCopyLen = left;
        if (left > 0) {
            System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
            _inputPtr += left;
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
                c = _decodeUTF8_3(c, d);
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
                c = _decodeUTF8_4(c, d);
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
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        int c;
        final byte[] inputBuffer = _inputBuffer;
        char[] outBuf = _textBuffer.getBufferWithoutReset();
        int outPtr = _textBuffer.getCurrentSegmentSize();

        // First things first: did we have partially decoded multi-byte UTF-8 character?
        if (_inputCopyLen > 0) {
            // NOTE: first byte stored in `_pending32` and we know we got one more byte for sure
            int next = _inputBuffer[_inputPtr++];
            switch (codes[_pending32]) {
            case 1: // 2-byte UTF
                c = _decodeUTF8_2(_pending32, next);
                break;
            case 2: // 3-byte UTF: did we have one or two bytes?
                if (_inputCopyLen == 1) {
                    if (_inputPtr >= _inputEnd) {
                        _inputCopy[0] = (byte) next;
                        _inputCopyLen = 2;
                        return JsonToken.NOT_AVAILABLE;
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
                        return JsonToken.NOT_AVAILABLE;
                    }
                    int i3 = _inputBuffer[_inputPtr++];
                    if (_inputPtr >= _inputEnd) {
                        _inputCopy[0] = (byte) next;
                        _inputCopy[1] = (byte) i3;
                        _inputCopyLen = 3;
                        return JsonToken.NOT_AVAILABLE;
                    }
                    c = _decodeUTF8_4(_pending32, next, i3, _inputBuffer[_inputPtr++]);
                    break;
                case 2:
                    if (_inputPtr >= _inputEnd) {
                        _inputCopy[1] = (byte) next;
                        _inputCopyLen = 3;
                        return JsonToken.NOT_AVAILABLE;
                    }
                    c = _decodeUTF8_4(_pending32, _inputCopy[0], next, _inputBuffer[_inputPtr++]);
                    break;
                case 3:
                default:
                    c = _decodeUTF8_4(_pending32, _inputCopy[0], _inputCopy[1], _inputBuffer[_inputPtr++]);
                    break;
                }
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
                _reportInvalidChar(_pending32);
                c = 0;
            }
            _inputCopyLen = 0; // just for safety
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }

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
                c = _decodeUTF8_3(c, d);
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
                c = _decodeUTF8_4(c, d);
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
        return JsonToken.NOT_AVAILABLE;
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Binary
    /**********************************************************************
     */
    
    protected final JsonToken _startRawBinary() throws IOException
    {
        // did not get it all; mark the state so we know where to return:
//        _tokenIncomplete = true;
        _minorState = MINOR_VALUE_BINARY_RAW_LEN;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

/*

    private final void _finishRawBinary() throws IOException
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
    
    protected final JsonToken _startQuotedBinary(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
//        _tokenIncomplete = true;
        _majorState = MINOR_VALUE_BINARY_7BIT_LEN;
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

    private final int _decodeUTF8_3(int c, int d) throws IOException
    {
        c &= 0x0F;
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        int e = _inputBuffer[_inputPtr++];
        if ((e & 0xC0) != 0x080) {
            _reportInvalidOther(e & 0xFF, _inputPtr);
        }
        return (c << 6) | (e & 0x3F);
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
    private final int _decodeUTF8_4(int c, int d) throws IOException
    {
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

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
        return _finishBigIntBody(_decodeVInt(), 0);
    }

    private final JsonToken _finishBigIntLen(int value, int bytesRead) throws IOException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                value = (value << 6) | (b & 0x3F);
                return _finishBigIntBody(value, 0);
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

    private final JsonToken _finishBigIntBody(int bytesToDecode, int buffered) throws IOException
    {
        if (_decode7BitEncoded(bytesToDecode, buffered)) { // got it all!
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
                value = (value << 6) | (b & 0x3F);
                return _finishBigDecimalBody(value, 0);
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

    private final JsonToken _finishBigDecimalBody(int bytesToDecode, int buffered) throws IOException
    {
        if (_decode7BitEncoded(bytesToDecode, buffered)) { // got it all!
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
    /* Shared text decoding methods
    /**********************************************************************
     */

    private final String _decodeShortASCII(int len) throws IOException
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

    private final String _decodeShortASCII(byte[] inBuf, int inPtr, int len) throws IOException
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
    private final String _decodeShortUnicode(int len) throws IOException
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

    private final String _decodeShortUnicode(byte[] inBuf, int inPtr, int len) throws IOException
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
                    _reportError("Invalid byte "+Integer.toHexString(i)+" in short Unicode text block");
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

    private final boolean _decode7BitEncoded(int bytesToDecode, int buffered) throws IOException
    {
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

    /*
    /**********************************************************************
    /* Handling of nested scope, state
    /**********************************************************************
     */

    private final JsonToken _startArrayScope() throws IOException
    {
        _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
        int st = MAJOR_ARRAY_ELEMENT;
        _majorState = st;
        _majorStateAfterValue = st;
        return (_currToken = JsonToken.START_ARRAY);
    }

    private final JsonToken _startObjectScope() throws IOException
    {
        _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
        int st = MAJOR_OBJECT_FIELD;
        _majorState = st;
        _majorStateAfterValue = st;
        return (_currToken = JsonToken.START_OBJECT);
    }
    
    private final JsonToken _closeArrayScope() throws IOException
    {
        if (!_parsingContext.inArray()) {
            _reportMismatchedEndMarker(']', '}');
        }
        JsonReadContext ctxt = _parsingContext.getParent();
        _parsingContext = ctxt;
        int st;
        if (ctxt.inObject()) {
            st = MAJOR_OBJECT_FIELD;
        } else if (ctxt.inArray()) {
            st = MAJOR_ARRAY_ELEMENT;
        } else {
            st = MAJOR_ROOT;
        }
        _majorState = st;
        _majorStateAfterValue = st;
        return (_currToken = JsonToken.END_ARRAY);
    }

    private final JsonToken _closeObjectScope() throws IOException
    {
        if (!_parsingContext.inObject()) {
            _reportMismatchedEndMarker('}', ']');
        }
        JsonReadContext ctxt = _parsingContext.getParent();
        _parsingContext = ctxt;
        int st;
        if (ctxt.inObject()) {
            st = MAJOR_OBJECT_FIELD;
        } else if (ctxt.inArray()) {
            st = MAJOR_ARRAY_ELEMENT;
        } else {
            st = MAJOR_ROOT;
        }
        _majorState = st;
        _majorStateAfterValue = st;
        return (_currToken = JsonToken.END_OBJECT);
    }

    /*
    /**********************************************************************
    /* Error reporting
    /**********************************************************************
     */

    private void _reportMissingHeader(int unmaskedFirstByte) throws IOException
    {
        String msg;
        int b = unmaskedFirstByte & 0xFF;
        // let's speculate on problem a bit, too
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
