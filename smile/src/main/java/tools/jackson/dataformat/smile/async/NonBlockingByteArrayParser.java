package tools.jackson.dataformat.smile.async;

import static tools.jackson.dataformat.smile.SmileConstants.BYTE_MARKER_END_OF_STRING;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.async.NonBlockingInputFeeder;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.sym.ByteQuadsCanonicalizer;
import tools.jackson.core.util.VersionUtil;

import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileReadFeature;
import tools.jackson.dataformat.smile.SmileUtil;

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

    // And from ParserBase:
//    protected int _inputPtr;
//    protected int _inputEnd;

    /**
     * Type of value being skipped after its length failed validation, if any
     * (used for error reporting)
     */
    private JsonToken _skippedValueType;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    // // States of header decoding (see "_finishHeader()"): first byte
    // // (0x3A) has been read and then...

    // ... expecting second byte (0x29)
    private final static int HEADER_STATE_BYTE_2 = 0;

    // ... expecting third byte (0x0A)
    private final static int HEADER_STATE_BYTE_3 = 1;

    // ... expecting fourth byte (version, flags)
    private final static int HEADER_STATE_BYTE_4 = 2;

    // ... [dataformats-binary#838] collapsed in-line header has been fully
    // decoded, but the following byte has not yet been seen
    private final static int HEADER_STATE_AFTER_INLINE = 3;

    /**
     * Length of Smile header, in bytes.
     */
    private final static int HEADER_LENGTH = 4;

    /**
     * Flag set when an in-line header is encountered right after a `null` token
     * (end marker, or another header): if so, header is not reported as a separate
     * `null` token (same as with blocking parser).
     *
     * @since 2.23
     */
    private boolean _collapseInlineHeader;

    public NonBlockingByteArrayParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, int smileFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(readCtxt, ioCtxt, parserFeatures, smileFeatures, sym);
    }

    /*
    /**********************************************************************
    /* AsyncInputFeeder impl
    /**********************************************************************
     */

    @Override
    public ByteArrayFeeder nonBlockingInputFeeder() {
        return this;
    }

    @Override
    public final boolean needMoreInput() {
        return (_inputPtr >=_inputEnd) && !_endOfInput && !_closed;
    }

    @Override
    public void feedInput(byte[] buf, int start, int end) throws JacksonException
    {
        // Must not have remaining input
        if (_inputPtr < _inputEnd) {
            _reportError("Still have %d undecoded bytes, should not call 'feedInput'", _inputEnd - _inputPtr);
        }
        if (end < start) {
            _reportError("Input end (%d) may not be before start (%d)", end, start);
        }
        if (_closed || _endOfInput) {
            _reportError("Parser closed, can not feed more input");
        }
        // Validate (including content being fed) before updating any state,
        // to leave parser untouched if this throws
        // (note: `_currInputProcessed + _inputEnd` is the total fed so far)
        _streamReadConstraints.validateDocumentLength(_currInputProcessed + _inputEnd
                + (end - start));
        // Time to update pointers first: [dataformats-binary#831] need to offset
        // `start` so that `_currInputProcessed + _inputPtr` is the absolute offset
        _currInputProcessed += _inputEnd - start;

        // And then update buffer settings
        _inputBuffer = buf;
        _inputPtr = start;
        _inputEnd = end;
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

//    public String nextName()
//    public boolean nextName(SerializableString str)
//    public String nextTextValue()
//    public int nextIntValue(int defaultValue)
//    public long nextLongValue(long defaultValue)
//    public Boolean nextBooleanValue()

    @Override
    public int releaseBuffered(OutputStream out) throws JacksonException {
        int avail = _inputEnd - _inputPtr;
        if (avail > 0) {
            try {
                out.write(_inputBuffer, _inputPtr, avail);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
        return avail;
    }

    /*
    /**********************************************************************
    /* Main-level decoding
    /**********************************************************************
     */

    @Override
    public JsonToken nextToken() throws JacksonException
    {
        // First: regardless of where we really are, need at least one more byte;
        // can simplify some of the checks by short-circuiting right away
        if (_inputPtr >= _inputEnd) {
            if (_closed) {
                return null;
            }
            // note: if so, do not even bother changing state
            if (_endOfInput) { // except for this special case
                // [dataformats-binary#830]: end-of-input in the middle of a token
                // is never valid (no Smile token can end at end-of-input)
                if ((_currToken == JsonToken.NOT_AVAILABLE) && !_skipCompleted()
                        && !_inlineHeaderCompleted()) {
                    _reportEOFInToken();
                }
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
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;
        int ch = _inputBuffer[_inputPtr++];

        switch (_majorState) {
        case MAJOR_INITIAL:
            if (SmileConstants.HEADER_BYTE_1 == ch) { // yes, initial header; should be good
                // minor state as 0, which is fine
                _majorState = MAJOR_ROOT;
                _minorState = MINOR_HEADER_INITIAL;
                return _finishHeader(HEADER_STATE_BYTE_2);
            }
            if (SmileReadFeature.REQUIRE_HEADER.enabledIn(_formatFeatures)) {
                _reportMissingHeader(ch);
            }
            // otherwise fine, just drop through to next state
            // (NOTE: it double-checks header; fine, won't match; just need the rest)
            _majorState = MAJOR_ROOT;
            return _startValue(ch);

        case MAJOR_ROOT: //
            if (SmileConstants.HEADER_BYTE_1 == ch) { // looks like a header
                _minorState = MINOR_HEADER_INLINE;
                // [dataformats-binary#838]: if preceded by `null` token (end marker,
                // or another header), header is not reported separately
                _collapseInlineHeader = (_currToken == null);
                return _finishHeader(HEADER_STATE_BYTE_2);
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
    protected final JsonToken _finishToken() throws JacksonException
    {
        // NOTE: caller ensures availability of at least one byte

        switch (_minorState) {
        case MINOR_HEADER_INITIAL:
        case MINOR_HEADER_INLINE:
            return _finishHeader(_pending32);

        case MINOR_PROPERTY_NAME_2BYTE:
            return _handleSharedName(_pending32 + (_inputBuffer[_inputPtr++] & 0xFF));

        case MINOR_PROPERTY_NAME_LONG:
            return _finishLongFieldName(_inputCopyLen);

        case MINOR_PROPERTY_NAME_SHORT_ASCII:
        case MINOR_PROPERTY_NAME_SHORT_UNICODE:
            {
                final int fullLen = _pending32;
                final int needed = fullLen - _inputCopyLen;
                final int avail = _inputEnd - _inputPtr;
                if (avail >= needed) { // got it all
                    System.arraycopy(_inputBuffer, _inputPtr, _inputCopy, _inputCopyLen, needed);
                    _inputPtr += needed;
                    String name = _findDecodedFromSymbols(_inputCopy, 0, fullLen);
                    if (name == null) {
                        name = (_minorState == MINOR_PROPERTY_NAME_SHORT_ASCII)
                                ? _decodeASCIIText(_inputCopy, 0, fullLen)
                                : _decodeShortUnicodeText(_inputCopy, 0, fullLen, true)
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
                    _streamReadContext.setCurrentName(name);
                    _majorState = MAJOR_OBJECT_VALUE;
                    return _updateToken(JsonToken.PROPERTY_NAME);
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
            return _finishBigDecimalScale(_pending32, _inputCopyLen);
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
                    String text = (_minorState == MINOR_VALUE_STRING_SHORT_ASCII)
                            ? _decodeASCIIText(_inputCopy, 0, fullLen)
                            : _decodeShortUnicodeText(_inputCopy, 0, fullLen, false);
                    if (_seenStringValueCount >= 0) { // shared String values enabled
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
        case MINOR_VALUE_SKIP_7BIT_BODY:
            return _finishSkip7BitBody();
        default:
        }
        throw new IllegalStateException("Illegal state when trying to complete token: majorState="+_majorState);
    }

    // [dataformats-binary#830]: Reports end-of-input encountered when decoding
    // of a token is incomplete (current token is `NOT_AVAILABLE`)
    private final void _reportEOFInToken() throws JacksonException
    {
        final String desc;
        final JsonToken type;
        switch (_minorState) {
        case MINOR_HEADER_INITIAL:
        case MINOR_HEADER_INLINE:
            desc = "Smile header";
            type = null;
            break;
        case MINOR_PROPERTY_NAME_2BYTE:
        case MINOR_PROPERTY_NAME_LONG:
        case MINOR_PROPERTY_NAME_SHORT_ASCII:
        case MINOR_PROPERTY_NAME_SHORT_UNICODE:
            desc = "Property name";
            type = JsonToken.PROPERTY_NAME;
            break;
        case MINOR_VALUE_NUMBER_INT:
        case MINOR_VALUE_NUMBER_LONG:
        case MINOR_VALUE_NUMBER_BIGINT_LEN:
        case MINOR_VALUE_NUMBER_BIGINT_BODY:
            desc = "Number value";
            type = JsonToken.VALUE_NUMBER_INT;
            break;
        case MINOR_VALUE_NUMBER_FLOAT:
        case MINOR_VALUE_NUMBER_DOUBLE:
        case MINOR_VALUE_NUMBER_BIGDEC_SCALE:
        case MINOR_VALUE_NUMBER_BIGDEC_LEN:
        case MINOR_VALUE_NUMBER_BIGDEC_BODY:
            desc = "Number value";
            type = JsonToken.VALUE_NUMBER_FLOAT;
            break;
        case MINOR_VALUE_SKIP_7BIT_BODY:
            desc = "Number value (being skipped)";
            type = _skippedValueType;
            break;
        case MINOR_VALUE_STRING_SHORT_ASCII:
        case MINOR_VALUE_STRING_SHORT_UNICODE:
        case MINOR_VALUE_STRING_LONG_ASCII:
        case MINOR_VALUE_STRING_LONG_UNICODE:
        case MINOR_VALUE_STRING_SHARED_2BYTE:
            desc = "String value";
            type = JsonToken.VALUE_STRING;
            break;
        case MINOR_VALUE_BINARY_RAW_LEN:
        case MINOR_VALUE_BINARY_RAW_BODY:
            desc = "Binary value (raw)";
            type = JsonToken.VALUE_EMBEDDED_OBJECT;
            break;
        case MINOR_VALUE_BINARY_7BIT_LEN:
        case MINOR_VALUE_BINARY_7BIT_BODY:
            desc = "Binary value (7-bit)";
            type = JsonToken.VALUE_EMBEDDED_OBJECT;
            break;
        default:
            desc = "token (internal state: "+_minorState+")";
            type = null;
        }
        // Nothing more can be decoded: close (releasing buffers) as with
        // regular end-of-input, then report
        _majorState = MAJOR_CLOSED;
        close();
        _updateTokenToNull();
        _reportInvalidEOF(" in "+desc, type);
    }

    // Whether we are in the state of skipping content of a value that failed
    // validation, but all of content has been skipped (value is complete)
    private final boolean _skipCompleted() {
        return (_minorState == MINOR_VALUE_SKIP_7BIT_BODY) && (_pending64 == 0L);
    }

    // [dataformats-binary#838]: collapsed in-line header fully decoded,
    // only waiting to see the following byte
    private final boolean _inlineHeaderCompleted() {
        return (_minorState == MINOR_HEADER_INLINE) && (_pending32 == HEADER_STATE_AFTER_INLINE);
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
    protected JsonToken _finishHeader(int state) throws JacksonException
    {
        int ch = 0;
        String errorDesc = null;

        switch (state) {
        case HEADER_STATE_BYTE_2:
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                return _updateTokenToNA();
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch!= SmileConstants.HEADER_BYTE_2) {
                errorDesc = "Malformed content: signature not valid, starts with 0x3a but followed by 0x%s, not 0x29";
                break;
            }
            state = HEADER_STATE_BYTE_3;
            // fall through
        case HEADER_STATE_BYTE_3:
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                return _updateTokenToNA();
            }
            ch = _inputBuffer[_inputPtr++];
            if (ch != SmileConstants.HEADER_BYTE_3) {
                errorDesc = "Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x%s not 0x0A";
                break;
            }
            state = HEADER_STATE_BYTE_4;
            // fall through
        case HEADER_STATE_BYTE_4:
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                return _updateTokenToNA();
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
            _updateTokenToNull();

            // Mild difference here: initial marker not reported separately, but in-line
            // ones need to be reported as `null` tokens as they are logical document end
            // markers (although should be collated with actual end markers)
            if (_minorState == MINOR_HEADER_INLINE) {
                if (!_collapseInlineHeader) {
                    return null;
                }
                // [dataformats-binary#838]: collapsed with preceding `null` token, but
                // to avoid deep recursion for long sequences of headers, only recurse
                // if next byte is not another header (same as blocking parser):
                // so need to see that byte first
                state = HEADER_STATE_AFTER_INLINE;
            } else {
                // [dataformats-binary#831]: if no more content available, token location
                // should still point to after the header (recursive call will update
                // otherwise)
                _tokenInputTotal = _currInputProcessed + _inputPtr;
                // Ok to use recursion in case of initial header, as well:
                return nextToken();
            }
            // fall through
        case HEADER_STATE_AFTER_INLINE: // [dataformats-binary#838]: check next byte
            if (_inputPtr >= _inputEnd) {
                _pending32 = state;
                // [dataformats-binary#831]: while waiting for more content, token
                // location should point to after the header
                _tokenInputTotal = _currInputProcessed + _inputPtr;
                return _updateTokenToNA();
            }
            _updateTokenToNull();
            if (_inputBuffer[_inputPtr] == SmileConstants.HEADER_BYTE_1) {
                // `null` token located at the start of the header,
                // same as with blocking parser
                _tokenInputTotal = _currInputProcessed + _inputPtr - HEADER_LENGTH;
                return null;
            }
            // [dataformats-binary#831]: if no more content available, token location
            // should still point to after the header (recursive call will update
            // otherwise)
            _tokenInputTotal = _currInputProcessed + _inputPtr;
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
    private final JsonToken _startValue(int ch) throws JacksonException
    {
        // [dataformats-binary#674] Update context index for non-Object contexts
        //   (Array, root).
        //  Q? Structural markers (END_ARRAY 0xF9, EOF 0xFF)?
        if (!_streamReadContext.inObject()) {
            // && (ch & 0xFF) != 0xF9 && (ch & 0xFF) != 0xFF) {
            _streamReadContext.valueRead();
        }
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
        case 7: // binary/long-String/long-shared/start-end-markers
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
                return _updateTokenToNA();
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
                return _updateTokenToNull();
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
     * that has to be either PROPERTY_NAME or END_OBJECT.
     */
    protected final JsonToken _startFieldName(int ch) throws JacksonException
    {
        // [dataformats-binary#674] Update context index for property entries
        //   (but not for END_OBJECT marker 0xFB)
        if ((ch & 0xFF) != 0xFB) {
            _streamReadContext.valueRead();
        }
        switch ((ch >> 6) & 3) {
        case 0: // misc, including end marker
            switch (ch) {
            case 0x20: // empty String as name, legal if unusual
                _streamReadContext.setCurrentName("");
                _majorState = MAJOR_OBJECT_VALUE;
                return _updateToken(JsonToken.PROPERTY_NAME);
            case 0x30: // long shared
            case 0x31:
            case 0x32:
            case 0x33:
                if (_inputPtr < _inputEnd) {
                    return _handleSharedName(((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF));
                }
                {
                    _minorState = MINOR_PROPERTY_NAME_2BYTE;
                    _pending32 = (ch & 0x3) << 8;
                    return _updateTokenToNA();
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
                    _streamReadContext.setCurrentName(name);
                    _majorState = MAJOR_OBJECT_VALUE;
                    return _updateToken(JsonToken.PROPERTY_NAME);
                }
                // Nope: need to copy
                _pending32 = len;
                _inputCopyLen = left;
                if (left > 0) {
                    _inputPtr = inputPtr + left;
                    System.arraycopy(_inputBuffer, inputPtr, _inputCopy, 0, left);
                }
            }
            _minorState = MINOR_PROPERTY_NAME_SHORT_ASCII;
            return _updateTokenToNA();

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
                        name = _decodeShortUnicodeText(_inputBuffer, inputPtr, len, true);
                        name = _addDecodedToSymbols(len, name);
                    }
                    if (_seenNames != null) {
                        if (_seenNameCount >= _seenNames.length) {
                         _seenNames = _expandSeenNames(_seenNames);
                        }
                        _seenNames[_seenNameCount++] = name;
                    }
                    _streamReadContext.setCurrentName(name);
                    _majorState = MAJOR_OBJECT_VALUE;
                    return _updateToken(JsonToken.PROPERTY_NAME);
                }
                // Nope: need to copy
                _pending32 = len;
                _inputCopyLen = left;
                if (left > 0) {
                    _inputPtr = inputPtr + left;
                    System.arraycopy(_inputBuffer, inputPtr, _inputCopy, 0, left);
                }
                _minorState = MINOR_PROPERTY_NAME_SHORT_UNICODE;
                return _updateTokenToNA();
            }
        }
        // Other byte values are illegal
        throw _constructReadException(
"Invalid type marker byte 0x%02x for expected property name (or END_OBJECT marker)", ch & 0xFF);
    }

    private final JsonToken _finishLongFieldName(int outPtr) throws JacksonException
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
                _minorState = MINOR_PROPERTY_NAME_LONG;
                _inputCopyLen = outPtr;
                return _updateTokenToNA();
            }
            // otherwise increase copy buffer length; but before that, verify
            // name length does not exceed maximum allowed, so that an unbounded
            // name cannot be buffered in full first ([dataformats-binary#726]).
            // Note that buffer is exactly full at this point, so `outPtr` is
            // the length of the name decoded so far.
            _streamReadConstraints.validateNameLength(outPtr);
            int oldLen = copyBuffer.length;
            int incr = Math.min(64000, oldLen >> 1);
            _inputCopy = copyBuffer = Arrays.copyOf(_inputCopy, oldLen + incr);
            // and loop again
        }

        // But if we get here, we got it all, only need to create quads etc
        _inputPtr = srcPtr;
        // [dataformats-binary#726]: check before symbol table lookup below, since
        // a hit would otherwise bypass validation for all but the first occurrence
        _streamReadConstraints.validateNameLength(outPtr);
        int[] quads = _quadBuffer;
        int qlen = (outPtr + 3) >> 2; // last quad may be partial

        if (quads.length < qlen) {
            _quadBuffer = quads = Arrays.copyOf(quads, qlen + 16);
        }
        int in = 0;
        int quadCount = 0;

        for (final int inEnd = (outPtr & ~3); in < inEnd; in += 4) {
            quads[quadCount++] = _decodeQuad(copyBuffer, in);
        }
        // and possibly more... ?
        if (in < outPtr) { // 1 - 3 bytes left over
            quads[quadCount++] = _decodePartialQuadForNulls(copyBuffer, in, outPtr - in);
        }

        String name = _symbols.findName(quads, quadCount);
        if (name == null) {
            name = _decodeLongUnicodeName(copyBuffer, 0, outPtr);
            // [dataformats-binary#761]: also add to symbol table, the way blocking
            // parser does, so that repeat occurrences need not be decoded again
            // (5-May-2023, ckozak: [core#1015] respect CANONICALIZE_FIELD_NAMES)
            if (_symbolsCanonical) {
                name = _symbols.addName(name, quads, quadCount);
            }
        }
        if (_seenNames != null) {
           if (_seenNameCount >= _seenNames.length) {
               _seenNames = _expandSeenNames(_seenNames);
           }
           _seenNames[_seenNameCount++] = name;
        }
        _streamReadContext.setCurrentName(name);
        _majorState = MAJOR_OBJECT_VALUE;
        return _updateToken(JsonToken.PROPERTY_NAME);
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Strings, short (length-prefix)
    /**********************************************************************
     */

    private final JsonToken _startShortASCII(final int len) throws JacksonException
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
        return _updateTokenToNA();
    }

    private final JsonToken _startShortUnicode(final int len) throws JacksonException
    {
        final int inPtr = _inputPtr;
        final int left = _inputEnd - inPtr;
        if (len <= left) { // gotcha!
            _inputPtr = inPtr + len;
            String text = _decodeShortUnicodeText(_inputBuffer, inPtr, len, false);
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
        return _updateTokenToNA();
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Strings, long (end marker)
    /**********************************************************************
     */

    /**
     * Helper method called when a long (end-marker terminated) String value
     * has been fully decoded.
     */
    private final JsonToken _longStringComplete(int outPtr) throws JacksonException
    {
        _textBuffer.setCurrentLength(outPtr);
        // Mark value complete first, so that if length check fails, parser
        // is not left in the middle of String value (to continue from it
        // if more tokens are requested)
        JsonToken t = _valueComplete(JsonToken.VALUE_STRING);
        // [dataformats-binary#824]: `TextBuffer` only validates length when
        //   finishing a segment or building a `String`/`char[]`, but not for
        //   `getTextBuffer()`, `size()` or `contentsToWriter()`, so check here
        _streamReadConstraints.validateStringLength(_textBuffer.size());
        return t;
    }

    private final JsonToken _startLongASCII() throws JacksonException
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
                    return _longStringComplete(outPtr);
                }
                outBuf[outPtr++] = (char) b;
            } while (--left > 0);
            _inputPtr = inPtr;
        }
        // denote current length; no partial input to save
        _textBuffer.setCurrentLength(outPtr);
        _minorState = MINOR_VALUE_STRING_LONG_ASCII;
        return _updateTokenToNA();
    }

    private final JsonToken _finishLongASCII() throws JacksonException
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
                    return _longStringComplete(outPtr);
                }
                outBuf[outPtr++] = (char) b;
            } while (--left > 0);
            _inputPtr = inPtr;
        }
        // denote current length; no partial input to save
        _textBuffer.setCurrentLength(outPtr);
        return JsonToken.NOT_AVAILABLE;
    }

    protected final JsonToken _startLongUnicode() throws JacksonException
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
                return _longStringComplete(outPtr);
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
        return _updateTokenToNA();
    }

    private final JsonToken _finishLongUnicode() throws JacksonException
    {
        // First things first: did we have partially decoded multi-byte UTF-8 character?
        if (_inputCopyLen > 0) {
            if (!_finishPartialUnicodeChar()) {
                return _updateTokenToNA();
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
                return _longStringComplete(outPtr);
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

    private final boolean _finishPartialUnicodeChar() throws JacksonException
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

    private final int _decodeUTF8_2(int c, int d) throws JacksonException
    {
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private final int _decodeUTF8_3(int c, int d, int e) throws JacksonException
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
    private final int _decodeUTF8_4(int c, int d, int e, int f) throws JacksonException
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

    private final JsonToken _startInt() throws JacksonException
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

    private final JsonToken _finishInt(int value, int bytesRead) throws JacksonException
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
        return _updateTokenToNA();
    }

    private final JsonToken _startLong() throws JacksonException
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

    private final JsonToken _finishLong(long value, int bytesRead) throws JacksonException
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
        return _updateTokenToNA();
    }

    private final JsonToken _startBigInt() throws JacksonException
    {
        _initByteArrayBuilder();
        return _finishBigIntLen(0, 0);
    }

    private final JsonToken _finishBigIntLen(int value, int bytesRead) throws JacksonException
    {
        final int len = _decodeUnsignedVInt(value, bytesRead, JsonToken.VALUE_NUMBER_INT);
        if (len < 0) {
            _minorState = MINOR_VALUE_NUMBER_BIGINT_LEN;
            return _updateTokenToNA();
        }
        return _startBigIntBody(len);
    }

    private final JsonToken _startBigIntBody(int len) throws JacksonException
    {
        // Validate declared length before buffering content
        try {
            _streamReadConstraints.validateIntegerLength(len);
        } catch (StreamConstraintsException e) {
            throw _skip7BitBodyAfterFailure(len, e, JsonToken.VALUE_NUMBER_INT);
        }
        _pending32 = len;
        _inputCopyLen = 0;
        return _finishBigIntBody();
    }

    private final JsonToken _finishBigIntBody() throws JacksonException
    {
        if (_decode7BitEncoded()) { // got it all!
            final byte[] array = _byteArrayBuilder.toByteArray();
            // [dataformats-binary#257]: 0-length special case to handle
            if (array.length == 0) {
                _numberBigInt = BigInteger.ZERO;
            } else {
                _numberBigInt = new BigInteger(array);
            }
            _numberType = NumberType.BIG_INTEGER;
            _numTypesValid = NR_BIGINT;
            return _valueComplete(JsonToken.VALUE_NUMBER_INT);
        }
        _minorState = MINOR_VALUE_NUMBER_BIGINT_BODY;
        return _updateTokenToNA();
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: numbers, floating-point
    /**********************************************************************
     */

    protected final JsonToken _startFloat() throws JacksonException
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

    protected final JsonToken _finishFloat(int value, int bytesRead) throws JacksonException
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
        return _updateTokenToNA();
    }

    protected final JsonToken _startDouble() throws JacksonException
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

    protected final JsonToken _finishDouble(long value, int bytesRead) throws JacksonException
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
        return _updateTokenToNA();
    }

    private final JsonToken _startBigDecimal() throws JacksonException
    {
        _initByteArrayBuilder();
        return _finishBigDecimalScale(0, 0);
    }

    private final JsonToken _finishBigDecimalScale(int value, int bytesRead) throws JacksonException
    {
        final int scale = _decodeUnsignedVInt(value, bytesRead, JsonToken.VALUE_NUMBER_FLOAT);
        if (scale < 0) {
            _minorState = MINOR_VALUE_NUMBER_BIGDEC_SCALE;
            return _updateTokenToNA();
        }
        // note! Scale stored here, need _pending32 for byte length
        _pending64 = scale;
        return _finishBigDecimalLen(0, 0);
    }

    private final JsonToken _finishBigDecimalLen(int value, int bytesRead) throws JacksonException
    {
        final int len = _decodeUnsignedVInt(value, bytesRead, JsonToken.VALUE_NUMBER_FLOAT);
        if (len < 0) {
            _minorState = MINOR_VALUE_NUMBER_BIGDEC_LEN;
            return _updateTokenToNA();
        }
        // Validate declared length before buffering content
        try {
            _streamReadConstraints.validateFPLength(len);
        } catch (StreamConstraintsException e) {
            throw _skip7BitBodyAfterFailure(len, e, JsonToken.VALUE_NUMBER_FLOAT);
        }
        _pending32 = len;
        _inputCopyLen = 0;
        return _finishBigDecimalBody();
    }

    // Called when declared length of 7-bit encoded content fails validation:
    // arranges for content to be skipped (on next call to nextToken()), so that
    // parsing may continue with the next token. Returns the validation failure
    // for caller to throw
    private final StreamConstraintsException _skip7BitBodyAfterFailure(int len,
            StreamConstraintsException fail, JsonToken valueType)
        throws StreamConstraintsException
    {
        _pending32 = len;
        _pending64 = _encoded7BitLength(len);
        _skippedValueType = valueType;
        _minorState = MINOR_VALUE_SKIP_7BIT_BODY;
        // Failed value still counts as a token (as with blocking parser)
        try {
            _updateToken(valueType);
        } catch (StreamConstraintsException e) {
            e.addSuppressed(fail);
            throw e;
        } finally {
            _updateTokenToNA();
        }
        return fail;
    }

    private final JsonToken _finishSkip7BitBody() throws JacksonException
    {
        // Same limit as with blocking parser (which cannot skip more)
        if (_pending64 > Integer.MAX_VALUE) {
            _reportError(
"Invalid content: invalid 7-bit binary encoded byte length (0x%X) exceeds maximum valid value",
                    _pending32);
        }
        final int count = (int) Math.min(_inputEnd - _inputPtr, _pending64);
        _inputPtr += count;
        _pending64 -= count;
        if (_pending64 > 0) {
            return _updateTokenToNA();
        }
        // All skipped: no value to return, so continue with the next token
        _majorState = _majorStateAfterValue;
        _currToken = null;
        return nextToken();
    }

    private final JsonToken _finishBigDecimalBody() throws JacksonException
    {
        if (_decode7BitEncoded()) { // got it all!
            // note: scale value is signed, needs zigzag, so:
            final int scale = SmileUtil.zigzagDecode((int) _pending64);
            final byte[] array = _byteArrayBuilder.toByteArray();
            // [dataformats-binary#257]: 0-length special case to handle
            if (array.length == 0) {
                _numberBigDecimal = BigDecimal.ZERO;
            } else {
                BigInteger bigInt = new BigInteger(array);
                _numberBigDecimal = new BigDecimal(bigInt, scale);
            }
            _numberType = NumberType.BIG_DECIMAL;
            _numTypesValid = NR_BIGDECIMAL;
            return _valueComplete(JsonToken.VALUE_NUMBER_FLOAT);
        }
        _minorState = MINOR_VALUE_NUMBER_BIGDEC_BODY;
        return _updateTokenToNA();
    }

    /*
    /**********************************************************************
    /* Internal methods: second-level parsing: Binary
    /**********************************************************************
     */

    protected final JsonToken _startRawBinary() throws JacksonException
    {
        return _finishRawBinaryLen(0, 0);
    }

    private final JsonToken _finishRawBinaryLen(int value, int bytesRead) throws JacksonException
    {
        final int len = _decodeUnsignedVInt(value, bytesRead, JsonToken.VALUE_EMBEDDED_OBJECT);
        if (len < 0) {
            _minorState = MINOR_VALUE_BINARY_RAW_LEN;
            return _updateTokenToNA();
        }
        return _startRawBinaryBody(len);
    }

    private final JsonToken _startRawBinaryBody(final int len) throws JacksonException
    {
        final int avail = _inputEnd - _inputPtr;
        // If all content is available, can allocate exact-size result and copy
        if (avail >= len) {
            _binaryValue = new byte[len];
            System.arraycopy(_inputBuffer, _inputPtr, _binaryValue, 0, len);
            _inputPtr += len;
            return _valueComplete(JsonToken.VALUE_EMBEDDED_OBJECT);
        }
        _pending32 = len;
        _inputCopyLen = 0;
        // If no content yet, wait: may get all of it with the next chunk
        if (avail == 0) {
            _minorState = MINOR_VALUE_BINARY_RAW_BODY;
            return _updateTokenToNA();
        }
        // Otherwise do not trust declared length for allocation (could be bogus):
        // accumulate content as it arrives
        _initByteArrayBuilder();
        return _appendRawBinaryBody();
    }

    private final JsonToken _finishRawBinaryBody() throws JacksonException
    {
        // Nothing buffered yet? Re-check if exact-size allocation possible
        if (_inputCopyLen == 0) {
            return _startRawBinaryBody(_pending32);
        }
        return _appendRawBinaryBody();
    }

    private final JsonToken _appendRawBinaryBody() throws JacksonException
    {
        final int needed = _pending32 - _inputCopyLen;
        final int count = Math.min(_inputEnd - _inputPtr, needed);
        _byteArrayBuilder.write(_inputBuffer, _inputPtr, count);
        _inputPtr += count;
        if (count < needed) {
            _inputCopyLen += count;
            _minorState = MINOR_VALUE_BINARY_RAW_BODY;
            return _updateTokenToNA();
        }
        _binaryValue = _byteArrayBuilder.toByteArray();
        return _valueComplete(JsonToken.VALUE_EMBEDDED_OBJECT);
    }

    private final JsonToken _start7BitBinary() throws JacksonException
    {
        _initByteArrayBuilder();
        return _finish7BitBinaryLen(0, 0);
    }

    private final JsonToken _finish7BitBinaryLen(int value, int bytesRead) throws JacksonException
    {
        final int len = _decodeUnsignedVInt(value, bytesRead, JsonToken.VALUE_EMBEDDED_OBJECT);
        if (len < 0) {
            _minorState = MINOR_VALUE_BINARY_7BIT_LEN;
            return _updateTokenToNA();
        }
        _pending32 = len;
        _inputCopyLen = 0;
        return _finish7BitBinaryBody();
    }

    private final JsonToken _finish7BitBinaryBody() throws JacksonException
    {
        if (_decode7BitEncoded()) { // got it all!
            _binaryValue = _byteArrayBuilder.toByteArray();
            return _valueComplete(JsonToken.VALUE_EMBEDDED_OBJECT);
        }
        _minorState = MINOR_VALUE_BINARY_7BIT_BODY;
        return _updateTokenToNA();
    }

    /*
    /**********************************************************************
    /* Shared text decoding methods
    /**********************************************************************
     */

    private final String _decodeASCIIText(byte[] inBuf, int inPtr, int len) throws JacksonException
    {
        // note: caller ensures we have enough bytes available
        String str = new String(inBuf, inPtr, len, StandardCharsets.US_ASCII);
        _textBuffer.resetWithString(str);
        return str;
    }

    /**
     * Helper method used to decode short Unicode string, length for which actual
     * length (in bytes) is known
     *
     * @param len Length between 1 and 64
     * @param isName Whether text is a property name (for error reporting)
     */
    private final String _decodeShortUnicodeText(byte[] inBuf, int inPtr, int len,
            boolean isName) throws JacksonException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        for (final int end = inPtr + len; inPtr < end; ) {
            int i = inBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // [dataformats-binary#833]: must not read past end of String
                if ((inPtr + code) > end) {
                    // Last -1 to compensate for byte that was read:
                    final int firstCharOffset = len - (end - inPtr) - 1;
                    if (isName) {
                        _reportTruncatedUTF8InName(len, firstCharOffset, i, code);
                    }
                    _reportTruncatedUTF8InString(len, firstCharOffset, i, code);
                }
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

    private final String _decodeLongUnicodeName(byte[] inBuf, int inPtr, int len) throws JacksonException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        // 26-Aug-2023, tatu: But it might not be big enough?
        final int neededSize = len + 8;
        if (outBuf.length < neededSize) {
            outBuf = _textBuffer.expandCurrentSegment(neededSize);
        }
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        // since we only check expansion for multi-byte chars, there must be
        // enough room for remaining bytes as all-ASCII
        int estSlack = outBuf.length - len;

        for (final int end = inPtr + len; inPtr < end; ) {
            int i = inBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // [dataformats-binary#833]: must not read past end of name
                if ((inPtr + code) > end) {
                    _reportInvalidEOF(" in long property name", JsonToken.PROPERTY_NAME);
                }
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
                    _reportError("Invalid byte 0x%02x in long Unicode text block (offset %d)", i & 0xFF, inPtr);
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

    private final int _fourBytesToInt(int ptr)  throws JacksonException
    {
        int i = _inputBuffer[ptr++]; // first 7 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 14 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 21
        i = (i << 7) + _inputBuffer[ptr++];
        return i;
    }

    private final int _decodeVInt() throws JacksonException
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

    /**
     * Helper method for decoding unsigned (31-bit) VInts used for lengths (and
     * {@code BigDecimal} scale), verifying there is no overflow. Decoding is
     * resumable: if not all bytes are available, partial value is stored in
     * {@code _pending32} and byte count in {@code _inputCopyLen}, and caller
     * needs to call this method with them when more input is available.
     *
     * @param value Value decoded so far (0 when starting)
     * @param bytesRead Number of bytes decoded so far (0 when starting)
     * @param valueType Type of value being decoded, for error messages
     *
     * @return Decoded value, if complete; -1 if more input is needed
     */
    private final int _decodeUnsignedVInt(int value, int bytesRead, JsonToken valueType)
        throws JacksonException
    {
        while (_inputPtr < _inputEnd) {
            int b = _inputBuffer[_inputPtr++];
            if (b < 0) { // got it all; these are last 6 bits
                // 4 x 7 + 6 == 34 bits, but only 31 allowed for unsigned int
                if ((bytesRead == 4) && ((value >>> 25) != 0)) {
                    _reportInvalidVInt(valueType, value >>> 21, b);
                }
                return (value << 6) | (b & 0x3F);
            }
            // can't get too big; 5 bytes is max
            if (++bytesRead >= 5) {
                _reportInvalidVInt(valueType, value >>> 21, b);
            }
            value = (value << 7) | b;
        }
        _pending32 = value;
        _inputCopyLen = bytesRead;
        return -1;
    }

    // Same as SmileParser._reportInvalidUnsignedVInt() but with type of value
    // being decoded passed (current token is not yet updated)
    private final void _reportInvalidVInt(JsonToken valueType, int firstByte, int lastByte)
        throws JacksonException
    {
        if (lastByte >= 0) {
            _reportError(
"Overflow in VInt (current token %s): 5th byte (0x%02X) of 5-byte sequence must have its highest bit set to indicate end",
valueType, lastByte);
        }
        _reportError(
"Overflow in VInt (current token %s): 1st byte (0x%02X) of 5-byte sequence must have its top 4 bits zeroes",
valueType, firstByte);
    }

    private final boolean _decode7BitEncoded() throws JacksonException
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

    protected final boolean _decode7BitEncodedTail(int bytesToDecode, int buffered) throws JacksonException
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
