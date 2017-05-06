package com.fasterxml.jackson.dataformat.smile.async;

import static com.fasterxml.jackson.dataformat.smile.SmileConstants.BYTE_MARKER_END_OF_STRING;

import java.io.IOException;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.VersionUtil;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileUtil;

public class NonBlockingByteArrayParser
    extends NonBlockingParserBase
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
    /* Decoding state
    /**********************************************************************
     */
    
    /**
     * Temporary storage for 32-bit values (int, float), as well as length markers
     * for length-prefixed values.
     */
    protected int _pending32;

    /**
     * For 64-bit values, we may use this for combining values
     */
    protected long _pending64;

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
    /* Main-level decoding
    /**********************************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException
    {
        // First: regardless of where we really are, need at least one more byte;
        // can simplify some of the checks by short-circuiting right away
        if (_inputPtr >= _inputEnd) {
            // note: if so, do not even bother changing state
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
                _majorState = MAJOR_HEADER;
                return _finishHeader();
            }
            if (_cfgRequireHeader) {
                _reportMissingHeader(ch);
            }
            // otherwise fine, just drop through to next state
            // (NOTE: it double-checks header; fine, won't match; just need the rest)
            _majorState = MAJOR_ROOT;
            return _startScalarValue(ch);
            
        case MAJOR_ROOT: // 
            if (SmileConstants.HEADER_BYTE_1 == ch) { // looks like a header
                _majorState = MAJOR_HEADER;
                _minorState = 0;
                return _finishHeader();
            }
            return _startScalarValue(ch);

        case MAJOR_OBJECT_FIELD: // field or end-object
            // expect name
            return _startFieldName(ch);
            
        case MAJOR_OBJECT_VALUE:
        case MAJOR_ARRAY_ELEMENT: // element or end-array
            return _startScalarValue(ch);

            // These types should never be not-incomplete so:
        case MAJOR_HEADER: // never gets here
        default:
        }
        VersionUtil.throwInternal();
        return null;
    }

    /**
     * Method called to finish parsing of a token, given partial decoded
     * state.
     */
    protected final JsonToken _finishToken() throws IOException
    {
        // first need to handle possible header, since that is usually not
        // exposed as an event (expect when it implies document boundary)
        switch (_majorState) {
        case MAJOR_HEADER:
            return _finishHeader();
        case MAJOR_VALUE_NUMBER_INT:
            return _nextInt(_minorState, _pending32);
        case MAJOR_VALUE_NUMBER_LONG:
            return _nextLong(_minorState, _pending64);
        case MAJOR_VALUE_NUMBER_BIGINT:
            return _nextBigInt(_minorState);
        case MAJOR_VALUE_NUMBER_FLOAT:
            return _nextFloat(_minorState, _pending32);
        case MAJOR_VALUE_NUMBER_DOUBLE:
            return _nextDouble(_minorState, _pending64);
        case MAJOR_VALUE_NUMBER_BIGDEC:
            return _nextBigDecimal(_minorState);

            // And then states that should never remain incomplete:
            
        case MAJOR_INITIAL: // should never be called
        case MAJOR_ROOT: // should never be called
        
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
        switch (_minorState) {
        case 0:
            if (_inputPtr >= _inputEnd) {
                return (_currToken = JsonToken.NOT_AVAILABLE);
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch!= SmileConstants.HEADER_BYTE_2) {
                _reportError("Malformed content: signature not valid, starts with 0x3a but followed by 0x"
                        +Integer.toHexString(ch)+", not 0x29");
            }
            _minorState = 1;
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
            _minorState = 2;
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
        // Ok to use recursion; not very often used, very unlikely to have a sequence of
        // headers (although allowed pointless)
        return nextToken();
    }

    /**
     * Helper method called to detect type of non-header token we have at
     * root level. Note that possible header has been ruled out by caller
     * and is not checked here.
     */
    private final JsonToken _startScalarValue(int ch) throws IOException
    {
        main_switch:
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
                    return _valueComplete(JsonToken.VALUE_STRING);
                case 0x01:
                    return _valueComplete(JsonToken.VALUE_NULL);
                case 0x02: // false
                    return _valueComplete(JsonToken.VALUE_FALSE);
                case 0x03: // 0x03 == true
                    return _valueComplete(JsonToken.VALUE_TRUE);
                case 0x04:
                    _majorState = MAJOR_VALUE_NUMBER_INT;
                    return _nextInt(0, 0);
                case 0x05:
                    _numberLong = 0;
                    _majorState = MAJOR_VALUE_NUMBER_LONG;
                    return _nextLong(0, 0L);
                case 0x06:
                    _majorState = MAJOR_VALUE_NUMBER_BIGINT;
                    return _nextBigInt(0);
                case 0x07: // illegal
                    break;
                case 0x08:
                    _pending32 = 0;
                    _majorState = MAJOR_VALUE_NUMBER_FLOAT;
                    _got32BitFloat = true;
                    return _nextFloat(0, 0);
                case 0x09:
                    _pending64 = 0L;
                    _majorState = MAJOR_VALUE_NUMBER_DOUBLE;
                    _got32BitFloat = false;
                    return _nextDouble(0, 0L);
                case 0x0A:
                    _majorState = MAJOR_VALUE_NUMBER_BIGDEC;
                    return _nextBigDecimal(0);
                case 0x0B: // illegal
                    break;
                case 0x1A:
                    // == 0x3A == ':' -> possibly switch; but should be handled elsewhere so...
                    break main_switch;
                }
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
            return _valueComplete(JsonToken.VALUE_NUMBER_INT);
        case 7: // binary/long-text/long-shared/start-end-markers
            switch (ch & 0x1F) {
            case 0x00: // long variable length ASCII
                return _startLongASCII();
            case 0x04: // long variable length unicode
                return _startLongUnicode();
            case 0x08: // binary, 7-bit
                return _nextQuotedBinary(0);
            case 0x0C: // long shared string
            case 0x0D:
            case 0x0E:
            case 0x0F:
                return _nextLongSharedString(0);
//                return _handleSharedString(((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF));
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
            _majorState = MAJOR_OBJECT_VALUE;
            return (_currToken = JsonToken.FIELD_NAME);

        case 2: // short ASCII; possibly doable
            {
                final int len = 1 + (ch & 0x3f);
                final int left = _inputEnd - _inputPtr;
                if (len <= left) { // gotcha!
                    String name = _findDecodedFromSymbols(len);
                    if (name != null) {
                        _inputPtr += len;
                    } else {
                        name = _decodeShortASCII(len);
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
                    System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
                }
            }
            _minorState = MINOR_FIELD_NAME_SHORT_ASCII;
            return JsonToken.NOT_AVAILABLE;

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
                        String name = _findDecodedFromSymbols(len);
                        if (name != null) {
                            _inputPtr += len;
                        } else {
                            name = _decodeShortUnicode(len);
                            name = _addDecodedToSymbols(len, name);
                        }
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
                return JsonToken.NOT_AVAILABLE;
            }
        }
        // Other byte values are illegal
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch)
                +" for expected field name (or END_OBJECT marker)");
        return null;
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
    private final String _findDecodedMedium(int len) throws IOException
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

    private int _quad1, _quad2;
    protected final String _addDecodedToSymbols(int len, String name)
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

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Strings
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
            return _valueComplete(JsonToken.FIELD_NAME);
        }
        // Nope: need to copy
        _pending32 = len;
        _inputCopyLen = left;
        if (left > 0) {
            System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
        }
        _minorState = MINOR_FIELD_NAME_SHORT_ASCII;
        return JsonToken.NOT_AVAILABLE;
    }

    private final JsonToken _startShortUnicode(final int len) throws IOException
    {
        final int left = _inputEnd - _inputPtr;
        if (len <= left) { // gotcha!
            String text = _decodeShortUnicode(len);
            if (_seenStringValueCount >= 0) { // shared text values enabled
                _addSeenStringValue(text);
            }
            return _valueComplete(JsonToken.FIELD_NAME);
        }
        // Nope: need to copy
        _pending32 = len;
        _inputCopyLen = left;
        if (left > 0) {
            System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, 0, left);
        }
        _majorState = MAJOR_SCALAR_VALUE;
        _minorState = MINOR_FIELD_NAME_SHORT_UNICODE;
        return JsonToken.NOT_AVAILABLE;
    }

    private final JsonToken _startLongASCII() throws IOException
    {
        return null;
        /*
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
        */
    }

    protected final JsonToken _startLongUnicode() throws IOException
    {
        // did not get it all; mark the state so we know where to return:
        _majorState = MAJOR_VALUE_STRING_LONG_UNICODE;
//        _tokenIncomplete = true;
//        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }
    
/*
    private final void _decodeLongUnicode() throws IOException
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
//        _tokenIncomplete = true;
        _majorState = MAJOR_VALUE_STRING_SHARED_LONG;
        _minorState = substate;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextRawBinary(int substate) throws IOException
    {
        // did not get it all; mark the state so we know where to return:
//        _tokenIncomplete = true;
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
//        _tokenIncomplete = true;
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
    /* Internal methods: second-level parsing: numbers
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
//                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_INT);
            }
            // can't get too big; 5 bytes is max
            if (++substate >= 5 ) {
                _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
            }
            value = (value << 7) | b;
        }
        // did not get it all; mark the state so we know where to return:
//        _tokenIncomplete = true;
        _minorState = substate;
        _pending32 = value;
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
//                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_INT);
            }
            // can't get too big; 10 bytes is max
            if (++substate >=  10) {
                _reportError("Corrupt input; 64-bit VInt extends beyond 10 data bytes");
            }
            value = (value << 7) | b;
        }
        // did not get it all; mark the state so we know where to return:
        //        _tokenIncomplete = true;
        _minorState = substate;
        _pending64 = value;
        _majorState = MAJOR_VALUE_NUMBER_LONG;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextBigInt(int substate) throws IOException
    {
        // !!! TBI
        //        _tokenIncomplete = true;
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
                //                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            }
        }
        //        _tokenIncomplete = true;
        _minorState = substate;
        _pending32 = value;
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
                //                _tokenIncomplete = false;
                return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
            }
        }
        //        _tokenIncomplete = true;
        _minorState = substate;
        _pending64 = value;
        _majorState = MAJOR_VALUE_NUMBER_DOUBLE;
        return (_currToken = JsonToken.NOT_AVAILABLE);
    }

    protected final JsonToken _nextBigDecimal(int substate) throws IOException
    {
        // !!! TBI
        //        _tokenIncomplete = true;
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
    
    /*
    /**********************************************************************
    /* Handling of nested scope, state
    /**********************************************************************
     */

    private final JsonToken _startArrayScope() throws IOException
    {
        _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
        _majorState = MAJOR_ARRAY_ELEMENT;
        return (_currToken = JsonToken.START_ARRAY);
    }

    private final JsonToken _startObjectScope() throws IOException
    {
        _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
        _majorState = MAJOR_OBJECT_FIELD;
        return (_currToken = JsonToken.START_OBJECT);
    }
    
    private final JsonToken _closeArrayScope() throws IOException
    {
        if (!_parsingContext.inArray()) {
            _reportMismatchedEndMarker(']', '}');
        }
        _parsingContext = _parsingContext.getParent();
        if (_parsingContext.inObject()) {
            _majorState = MAJOR_OBJECT_VALUE;
        } else if (_parsingContext.inArray()) {
            _majorState = MAJOR_ARRAY_ELEMENT;
        } else {
            _majorState = MAJOR_ROOT;
        }
        return (_currToken = JsonToken.END_ARRAY);
    }

    private final JsonToken _closeObjectScope() throws IOException
    {
        if (!_parsingContext.inObject()) {
            _reportMismatchedEndMarker('}', ']');
        }
        _parsingContext = _parsingContext.getParent();
        if (_parsingContext.inObject()) {
            _majorState = MAJOR_OBJECT_VALUE;
        } else if (_parsingContext.inArray()) {
            _majorState = MAJOR_ARRAY_ELEMENT;
        } else {
            _majorState = MAJOR_ROOT;
        }
        return (_currToken = JsonToken.END_OBJECT);
    }

    private final JsonToken _valueComplete(JsonToken t) throws IOException
    {
        if (_parsingContext.inObject()) {
            _majorState = MAJOR_OBJECT_FIELD;
        } else if (_parsingContext.inArray()) { // should already be the case bu
            _majorState = MAJOR_ARRAY_ELEMENT;
        } else {
            _majorState = MAJOR_ROOT;
        }
        _currToken = t;
        return t;
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
