package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
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
//    protected byte[] _inputBuffer = NO_BYTES;

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
        // in the middle of tokenization?
        if (_currToken == JsonToken.NOT_AVAILABLE) {
            return _finishToken();
        }

        // No: fresh new token; may or may not have existing one
        _numTypesValid = NR_UNKNOWN;
//            _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;

        if (_inputPtr >= _inputEnd) {
            _currToken = JsonToken.NOT_AVAILABLE;
            return _currToken;
        }

        int ch = _inputBuffer[_inputPtr++];

        switch (_majorState) {
        case MAJOR_INITIAL:
        case MAJOR_HEADER:
        case MAJOR_OBJECT_START:
        case MAJOR_OBJECT_FIELD:
        case MAJOR_OBJECT_VALUE:
        case MAJOR_ARRAY_START:
        case MAJOR_ARRAY_ELEMENT:
        case MAJOR_VALUE_BINARY_RAW:
        case MAJOR_VALUE_BINARY_7BIT:
        case MAJOR_VALUE_STRING_SHORT_ASCII:
        case MAJOR_VALUE_STRING_SHORT_UNICODE:
        case MAJOR_VALUE_STRING_LONG_ASCII:
        case MAJOR_VALUE_STRING_LONG_UNICODE:
        case MAJOR_VALUE_NUMBER_INT:
        case MAJOR_VALUE_NUMBER_LONG:
        case MAJOR_VALUE_NUMBER_FLOAT:
        case MAJOR_VALUE_NUMBER_DOUBLE:
        case MAJOR_VALUE_NUMBER_BIGINT:
        case MAJOR_VALUE_NUMBER_BIGDEC:
        }
        
        // Two main modes: values, and field names.
        if (_parsingContext.inObject() && _currToken != JsonToken.FIELD_NAME) {
            return (_currToken = _handleFieldName());
        }
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
                    _pendingInt = 0;
                    _majorState = MAJOR_VALUE_NUMBER_FLOAT;
                    _got32BitFloat = true;
                    return _nextFloat(0, 0);
                case 0x09:
                    _pendingLong = 0L;
                    _majorState = MAJOR_VALUE_NUMBER_DOUBLE;
                    _got32BitFloat = false;
                    return _nextDouble(0, 0L);
                case 0x0A:
                    _majorState = MAJOR_VALUE_NUMBER_BIGDEC;
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

    protected boolean handleSignature(boolean consumeFirstByte, boolean throwException)
            throws IOException
        {
            if (consumeFirstByte) {
                ++_inputPtr;
            }
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            if (_inputBuffer[_inputPtr] != SmileConstants.HEADER_BYTE_2) {
                if (throwException) {
                   _reportError("Malformed content: signature not valid, starts with 0x3a but followed by 0x"
                             +Integer.toHexString(_inputBuffer[_inputPtr])+", not 0x29");
                }
                return false;
            }
            if (++_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();           
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
                _loadMoreGuaranteed();           
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

}
