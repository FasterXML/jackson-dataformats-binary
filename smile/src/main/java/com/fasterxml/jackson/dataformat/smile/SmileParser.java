package com.fasterxml.jackson.dataformat.smile;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

import static com.fasterxml.jackson.dataformat.smile.SmileConstants.BYTE_MARKER_END_OF_STRING;

public class SmileParser extends SmileParserBase
{
    /**
     * Enumeration that defines all togglable features for Smile generators.
     */
    public enum Feature implements FormatFeature
    {
        /**
         * Feature that determines whether 4-byte Smile header is mandatory in input,
         * or optional. If enabled, it means that only input that starts with the header
         * is accepted as valid; if disabled, header is optional. In latter case,
         * settings for content are assumed to be defaults.
         */
        REQUIRE_HEADER(true)
        ;

        final boolean _defaultState;
        final int _mask;

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }

        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        @Override public boolean enabledByDefault() { return _defaultState; }
        @Override public int getMask() { return _mask; }
        @Override public boolean enabledIn(int flags) { return (flags & getMask()) != 0; }    
    }

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;

    /*
    /**********************************************************
    /* Input source config, state (from ex StreamBasedParserBase)
    /**********************************************************
     */

    /**
     * Input stream that can be used for reading more content, if one
     * in use. May be null, if input comes just as a full buffer,
     * or if the stream has been closed.
     */
    protected InputStream _inputStream;

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but in some cases pre-loaded buffer
     * is handed to the parser.
     */
    protected byte[] _inputBuffer;

    /**
     * Flag that indicates whether the input buffer is recycable (and
     * needs to be returned to recycler once we are done) or not.
     *<p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;

    /*
    /**********************************************************
    /* Additional parsing state
    /**********************************************************
     */

    /**
     * Type byte of the current token (as in)
     */
    protected int _typeAsInt;

    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete = false;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public SmileParser(IOContext ctxt, int parserFeatures, int smileFeatures,
            ObjectCodec codec,
            ByteQuadsCanonicalizer sym,
            InputStream in, byte[] inputBuffer, int start, int end,
            boolean bufferRecyclable)
    {
        super(ctxt, parserFeatures, smileFeatures, sym);        
        _objectCodec = codec;

        _inputStream = in;
        _inputBuffer = inputBuffer;
        _inputPtr = start;
        _inputEnd = end;
        _bufferRecyclable = bufferRecyclable;
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
    protected boolean handleSignature(boolean consumeFirstByte, boolean throwException) throws IOException
    {
        if (consumeFirstByte) {
            ++_inputPtr;
        }
        if (_nextByteGuaranteed() != SmileConstants.HEADER_BYTE_2) {
            if (throwException) {
            	_reportError("Malformed content: signature not valid, starts with 0x3a but followed by 0x"
            			+Integer.toHexString(_inputBuffer[_inputPtr])+", not 0x29");
            }
            return false;
        }
        if (_nextByteGuaranteed() != SmileConstants.HEADER_BYTE_3) {
            if (throwException) {
            	_reportError("Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x"
            			+Integer.toHexString(_inputBuffer[_inputPtr])+", not 0xA");
            }
            return false;
        }
        // Good enough; just need version info from 4th byte...
        int ch = _nextByteGuaranteed();
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

    /*
    /**********************************************************
    /* Former StreamBasedParserBase methods
    /**********************************************************
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
        return _inputStream;
    }

    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    // @since 2.8
    private final byte _nextByteGuaranteed() throws IOException
    {
        int ptr = _inputPtr;
        if (ptr < _inputEnd) {
            byte b = _inputBuffer[ptr];
            _inputPtr = ptr+1;
            return b;
        }
        _loadMoreGuaranteed();
        return _inputBuffer[_inputPtr++];
    }

    protected final void _loadMoreGuaranteed() throws IOException {
        if (!_loadMore()) { _reportInvalidEOF(); }
    }
    
    protected final boolean _loadMore() throws IOException
    {
        //_currInputRowStart -= _inputEnd;
        
        if (_inputStream != null) {
            int count = _inputStream.read(_inputBuffer, 0, _inputBuffer.length);
            _currInputProcessed += _inputEnd;
            _inputPtr = 0;
            if (count > 0) {
                _inputEnd = count;
                return true;
            }
            // important: move pointer to same as end, to keep location accurate
            _inputEnd = 0;
            // End of input
            _closeInput();
            // Should never return 0, so let's fail
            if (count == 0) {
                throw new IOException("InputStream.read() returned 0 characters when trying to read "+_inputBuffer.length+" bytes");
            }
        }
        return false;
    }
    
    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected final void _loadToHaveAtLeast(int minAvailable) throws IOException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            throw _constructError("Needed to read "+minAvailable+" bytes, reached end-of-input");
        }
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        _currInputProcessed += _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            //_currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                // End of input
                _closeInput();
                // Should never return 0, so let's fail
                if (count == 0) {
                    throw new IOException("InputStream.read() returned 0 characters when trying to read "+amount+" bytes");
                }
                throw _constructError("Needed to read "+minAvailable+" bytes, missed "+minAvailable+" before end-of-input");
            }
            _inputEnd += count;
        }
    }

    @Override
    protected void _closeInput() throws IOException
    {
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

    /*
    /**********************************************************
    /* Overridden methods
    /**********************************************************
     */

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
    protected void _releaseBuffers2()
    {
        if (_bufferRecyclable) {
            byte[] buf = _inputBuffer;
            if (buf != null) {
                _inputBuffer = null;
                _ioContext.releaseReadIOBuffer(buf);
            }
        }
    }

    /*
    /**********************************************************
    /* JsonParser impl
    /**********************************************************
     */
    
    @Override
    public JsonToken nextToken() throws IOException
    {
        _numTypesValid = NR_UNKNOWN;
        // For longer tokens (text, binary), we'll only read when requested
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _tokenOffsetForTotal = _inputPtr;
//        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;
        // Two main modes: values, and field names.
        if ((_currToken != JsonToken.FIELD_NAME) && _parsingContext.inObject()) {
            return (_currToken = _handleFieldName());
        }
        if (_inputPtr >= _inputEnd) {
            if (!_loadMore()) {
            	return _eofAsNextToken();
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        _typeAsInt = ch;
        switch (ch >> 5) {
        case 0: // short shared string value reference
            if (ch != 0) { // 0x0 is invalid
                return _handleSharedString(ch-1);
            }
            break;

        case 1: // simple literals, numbers
            {
                int typeBits = ch & 0x1F;
                if (typeBits < 4) {
                    switch (typeBits) {
                    case 0x00:
                        _textBuffer.resetWithEmpty();
                        return (_currToken = JsonToken.VALUE_STRING);
                    case 0x01:
                        return (_currToken = JsonToken.VALUE_NULL);
                    case 0x02: // false
                        return (_currToken = JsonToken.VALUE_FALSE);
                    default: // 0x03 == true
                        return (_currToken = JsonToken.VALUE_TRUE);
                    }
                }
                if (typeBits == 4) {
                    _finishInt();
                    return (_currToken = JsonToken.VALUE_NUMBER_INT);
                }
                // next 3 bytes define subtype
                if (typeBits <= 6) { // VInt (zigzag), BigInteger
                    _tokenIncomplete = true;
                    return (_currToken = JsonToken.VALUE_NUMBER_INT);
                }
                if (typeBits < 11 && typeBits != 7) { // floating-point
                    _tokenIncomplete = true;
                    return (_currToken = JsonToken.VALUE_NUMBER_FLOAT);
                }
                if (typeBits == 0x1A) { // == 0x3A == ':' -> possibly header signature for next chunk?
                    if (handleSignature(false, false)) {
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
                    _reportError("Unrecognized token byte 0x3A (malformed segment header?");
            	}
            }
            // and everything else is reserved, for now
            break;
        case 2: // tiny ASCII
            // fall through            
        case 3: // short ASCII
            // fall through
        case 4: // tiny Unicode
            // fall through
        case 5: // short Unicode
            // No need to decode, unless we have to keep track of back-references (for shared string values)
            if (_seenStringValueCount >= 0) { // shared text values enabled
                return _addSeenStringValue();
            }
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_STRING);
        case 6: // small integers; zigzag encoded
            _numberInt = SmileUtil.zigzagDecode(ch & 0x1F);
            _numTypesValid = NR_INT;
            _numberType = NumberType.INT;
            return (_currToken = JsonToken.VALUE_NUMBER_INT);
        case 7: // binary/long-text/long-shared/start-end-markers
            switch (ch & 0x1F) {
            case 0x00: // long variable length ASCII
            case 0x04: // long variable length unicode
                _tokenIncomplete = true;
                return (_currToken = JsonToken.VALUE_STRING);
            case 0x08: // binary, 7-bit (0xE8)
                _tokenIncomplete = true;
                return (_currToken = JsonToken.VALUE_EMBEDDED_OBJECT);
            case 0x0C: // long shared string (0xEC)
            case 0x0D:
            case 0x0E:
            case 0x0F:
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                }
                return _handleSharedString(((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF));
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
                _tokenIncomplete = true;
                return (_currToken = JsonToken.VALUE_EMBEDDED_OBJECT);
            case 0x1F: // 0xFF, end of content
                return (_currToken = null);
            }
            break;
        }
        // If we get this far, type byte is corrupt
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch & 0xFF)+" for expected value token");
        return null;
    }

    private final JsonToken _handleSharedString(int index) throws IOException
    {
        if (index >= _seenStringValueCount) {
            _reportInvalidSharedStringValue(index);
        }
        _textBuffer.resetWithString(_seenStringValues[index]);
        return (_currToken = JsonToken.VALUE_STRING);
    }

    private final JsonToken _addSeenStringValue() throws IOException
    {
        _finishToken();
        String v = _textBuffer.contentsAsString();
        if (_seenStringValueCount < _seenStringValues.length) {
            // !!! TODO: actually only store char[], first time around?
            _seenStringValues[_seenStringValueCount++] = v;
        } else {
            _expandSeenStringValues(v);
        }
        return (_currToken = JsonToken.VALUE_STRING);
    }
    
    private final void _expandSeenStringValues(String newText)
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
            newShared = Arrays.copyOf(oldShared, newSize);
        }
        _seenStringValues = newShared;
        _seenStringValues[_seenStringValueCount++] = newText;
    }

    /**
     * Method for forcing full read of current token, even if it might otherwise
     * only be read if data is accessed via {@link #getText} and similar methods.
     */
    @Override
    public void finishToken() throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
    }

    /*
    /**********************************************************
    /* Optimized accessors, isXxx, nextXxx (except for nextToken()
    /**********************************************************
     */

    // Not (yet?) overridden, as of 2.6
    /*
    public boolean hasTokenId(int id) {
        return super.hasTokenId(id);
    }
    */

    //public boolean isExpectedStartArrayToken() { return currentToken() == JsonToken.START_ARRAY; }

    //public boolean isExpectedStartObjectToken() { return currentToken() == JsonToken.START_OBJECT; }
    
    @Override
    public boolean nextFieldName(SerializableString str) throws IOException
    {
        // Two parsing modes; can only succeed if expecting field name, so handle that first:
        if (_currToken != JsonToken.FIELD_NAME && _parsingContext.inObject()) {
            // first, clear up state
            _numTypesValid = NR_UNKNOWN;
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            _tokenOffsetForTotal = _inputPtr;
            _binaryValue = null;

            byte[] nameBytes = str.asQuotedUTF8();
            final int byteLen = nameBytes.length;
            // need room for type byte, name bytes, possibly end marker, so:
            if ((_inputPtr + byteLen + 1) < _inputEnd) { // maybe...
                int ptr = _inputPtr;
                int ch = _inputBuffer[ptr++] & 0xFF;
                
                _typeAsInt = ch;
                main_switch:
                switch (ch >> 6) {
                case 0: // misc, including end marker
                    switch (ch) {
                    case 0x20: // empty String as name, legal if unusual
                        _currToken = JsonToken.FIELD_NAME;
                        _inputPtr = ptr;
                        _parsingContext.setCurrentName("");
                        return (byteLen == 0);
                    case 0x30: // long shared
                    case 0x31:
                    case 0x32:
                    case 0x33:
                        {
                            int index = ((ch & 0x3) << 8) + (_inputBuffer[ptr++] & 0xFF);
                            if (index >= _seenNameCount) {
                                _reportInvalidSharedName(index);
                            }
                            String name = _seenNames[index];
                            _parsingContext.setCurrentName(name);
                            _inputPtr = ptr;
                            _currToken = JsonToken.FIELD_NAME;
                            return (name.equals(str.getValue()));
                        }
                    //case 0x34: // long ASCII/Unicode name; let's not even try...
                    }
                    break;
                case 1: // short shared, can fully process
                    {
                        int index = (ch & 0x3F);
                        if (index >= _seenNameCount) {
                            _reportInvalidSharedName(index);
                        }
                        _parsingContext.setCurrentName(_seenNames[index]);
                        String name = _seenNames[index];
                        _parsingContext.setCurrentName(name);
                        _inputPtr = ptr;
                        _currToken = JsonToken.FIELD_NAME;
                        return (name.equals(str.getValue()));
                    }
                case 2: // short ASCII
                    {
                        int len = 1 + (ch & 0x3f);
                        if (len == byteLen) {
                            int i = 0;
                            for (; i < len; ++i) {
                                if (nameBytes[i] != _inputBuffer[ptr+i]) {
                                    break main_switch;
                                }
                            }
                            // yes, does match...
                            _inputPtr = ptr + len;
                            final String name = str.getValue();
                            if (_seenNames != null) {
                               if (_seenNameCount >= _seenNames.length) {
                                   _seenNames = _expandSeenNames(_seenNames);
                               }
                               _seenNames[_seenNameCount++] = name;
                            }
                            _parsingContext.setCurrentName(name);
                            _currToken = JsonToken.FIELD_NAME;
                            return true;
                        }
                    }
                    break;
                case 3: // short Unicode
                    // all valid, except for 0xFF
                    {
                        int len = (ch & 0x3F);
                        if (len > 0x37) {
                            if (len == 0x3B) {
                                _currToken = JsonToken.END_OBJECT;
                                if (!_parsingContext.inObject()) {
                                    _reportMismatchedEndMarker('}', ']');
                                }
                                _inputPtr = ptr;
                                _parsingContext = _parsingContext.getParent();
                                return false;
                            }
                            // error, but let's not worry about that here
                            break;
                        }
                        len += 2; // values from 2 to 57...
                        if (len == byteLen) {
                            int i = 0;
                            for (; i < len; ++i) {
                                if (nameBytes[i] != _inputBuffer[ptr+i]) {
                                    break main_switch;
                                }
                            }
                            // yes, does match...
                            _inputPtr = ptr + len;
                            final String name = str.getValue();
                            if (_seenNames != null) {
                               if (_seenNameCount >= _seenNames.length) {
                                   _seenNames = _expandSeenNames(_seenNames);
                               }
                               _seenNames[_seenNameCount++] = name;
                            }
                            _parsingContext.setCurrentName(name);
                            _currToken = JsonToken.FIELD_NAME;
                            return true;
                        }
                    }
                    break;
                }
            }
            // wouldn't fit in buffer, just fall back to default processing
        }
        // otherwise just fall back to default handling; should occur rarely
        return (nextToken() == JsonToken.FIELD_NAME) && str.getValue().equals(getCurrentName());
    }

    @Override
    public String nextFieldName() throws IOException
    {
        // Two parsing modes; can only succeed if expecting field name, so handle that first:
        if (_currToken != JsonToken.FIELD_NAME && _parsingContext.inObject()) {
            // first, clear up state
            _numTypesValid = NR_UNKNOWN;
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            _tokenOffsetForTotal = _inputPtr;
            _binaryValue = null;

            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            // is this needed?
            _typeAsInt = ch;
            switch (ch >> 6) {
            case 0: // misc, including end marker
                switch (ch) {
                case 0x20: // empty String as name, legal if unusual
                    _parsingContext.setCurrentName("");
                    _currToken = JsonToken.FIELD_NAME;
                    return "";
                case 0x30: // long shared
                case 0x31:
                case 0x32:
                case 0x33:
                    if (_inputPtr >= _inputEnd) {
                        _loadMoreGuaranteed();
                    }
                    {
                        int index = ((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
                        if (index >= _seenNameCount) {
                            _reportInvalidSharedName(index);
                        }
                        String name = _seenNames[index];
                        _parsingContext.setCurrentName(name);
                        _currToken = JsonToken.FIELD_NAME;
                        return name;
                    }
                case 0x34: // long ASCII/Unicode name
                    _handleLongFieldName();
                    _currToken = JsonToken.FIELD_NAME;
                    return getCurrentName();
                }
                break;
            case 1: // short shared, can fully process
                {
                    int index = (ch & 0x3F);
                    if (index >= _seenNameCount) {
                        _reportInvalidSharedName(index);
                    }
                    String name = _seenNames[index];
                    _parsingContext.setCurrentName(name);
                    _currToken = JsonToken.FIELD_NAME;
                    return name;
                }
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
                    _currToken = JsonToken.FIELD_NAME;
                    return name;
                }
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
                            _currToken = JsonToken.END_OBJECT;
                            return null;
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
                        _currToken = JsonToken.FIELD_NAME;
                        return name;
                    }
                }
                break;
            }
            // Other byte values are illegal
            _reportError("Invalid type marker byte 0x"+Integer.toHexString(_typeAsInt)+" for expected field name (or END_OBJECT marker)");
            return null;
        }
        
        // otherwise just fall back to default handling; should occur rarely
        return (nextToken() == JsonToken.FIELD_NAME) ? getCurrentName() : null;
    }

    @Override
    public String nextTextValue() throws IOException
    {
        // can't get text value if expecting name, so
        if (!_parsingContext.inObject() || _currToken == JsonToken.FIELD_NAME) {
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            int ptr = _inputPtr;
            if (ptr >= _inputEnd) {
                if (!_loadMore()) {
                	_eofAsNextToken();
                    return null;
                }
                ptr = _inputPtr;
            }
            _tokenOffsetForTotal = ptr;
//          _tokenInputTotal = _currInputProcessed + _inputPtr;
            int ch = _inputBuffer[ptr++] & 0xFF;
            _typeAsInt = ch;

            // also: clear any data retained so far
            _binaryValue = null;

            switch (ch >> 5) {
            case 0: // short shared string value reference
                if (ch != 0) {
                // _handleSharedString...
                    --ch;
                    if (ch >= _seenStringValueCount) {
                        _reportInvalidSharedStringValue(ch);
                    }
                    _inputPtr = ptr;
                    String text = _seenStringValues[ch];
                    _textBuffer.resetWithString(text);
                    _currToken = JsonToken.VALUE_STRING;
                    return text;
                } else {
                    // important: this is invalid, don't accept
                    _reportError("Invalid token byte 0x00");
                }

            case 1: // simple literals, numbers
                {
                    int typeBits = ch & 0x1F;
                    if (typeBits == 0x00) {
                        _inputPtr = ptr;
                        _textBuffer.resetWithEmpty();
                        _currToken = JsonToken.VALUE_STRING;
                        return "";
                    }
                }
                break;
            case 2: // tiny ASCII
                // fall through            
            case 3: // short ASCII
                _currToken = JsonToken.VALUE_STRING;
                _inputPtr = ptr;
                {
                    final String text = _decodeShortAsciiValue(1 + (ch & 0x3F));
                    if (_seenStringValueCount >= 0) { // shared text values enabled
                        if (_seenStringValueCount < _seenStringValues.length) {
                            _seenStringValues[_seenStringValueCount++] = text;
                        } else {
                            _expandSeenStringValues(text);
                        }
                    }
                    return text;
                }

            case 4: // tiny Unicode
                // fall through
            case 5: // short Unicode
                _currToken = JsonToken.VALUE_STRING;
                _inputPtr = ptr;
                {
                    final String text = _decodeShortUnicodeValue(2 + (ch & 0x3F));
                    if (_seenStringValueCount >= 0) { // shared text values enabled
                        if (_seenStringValueCount < _seenStringValues.length) {
                            _seenStringValues[_seenStringValueCount++] = text;
                        } else {
                            _expandSeenStringValues(text);
                        }
                    }
                    return text;
                }
            case 6: // small integers; zigzag encoded
                break;
            case 7: // binary/long-text/long-shared/start-end-markers
                // TODO: support longer strings too?
                /*
                switch (ch & 0x1F) {
                case 0x00: // long variable length ASCII
                case 0x04: // long variable length unicode
                    _tokenIncomplete = true;
                    return (_currToken = JsonToken.VALUE_STRING);
                case 0x08: // binary, 7-bit
                    break main;
                case 0x0C: // long shared string
                case 0x0D:
                case 0x0E:
                case 0x0F:
                    if (_inputPtr >= _inputEnd) {
                        _loadMoreGuaranteed();
                    }
                    return _handleSharedString(((ch & 0x3) << 8) + (_inputBuffer[_inputPtr++] & 0xFF));
                }
                break;
                */
                break;
            }
        }
        // otherwise fall back to generic handling (note: we do NOT assign 'ptr')
        return (nextToken() == JsonToken.VALUE_STRING) ? getText() : null;
    }

    @Override
    public int nextIntValue(int defaultValue) throws IOException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getIntValue();
        }
        return defaultValue;
    }

    @Override
    public long nextLongValue(long defaultValue) throws IOException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getLongValue();
        }
        return defaultValue;
    }

    @Override
    public Boolean nextBooleanValue() throws IOException
    {
        JsonToken t = nextToken();
        if (t == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (t == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        return null;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
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
        if (_tokenIncomplete) {
            _tokenIncomplete = false;
            // Let's inline part of "_finishToken", common case
            int tb = _typeAsInt;
            int type = (tb >> 5);
            if (type == 2 || type == 3) { // tiny & short ASCII
                return _decodeShortAsciiValue(1 + (tb & 0x3F));
            }
            if (type == 4 || type == 5) { // tiny & short Unicode
                 // short unicode; note, lengths 2 - 65  (off-by-one compared to ASCII)
                return _decodeShortUnicodeValue(2 + (tb & 0x3F));
            }
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        JsonToken t = _currToken;
        if (t == null) { // null only before/after document
            return null;
        }
        if (t == JsonToken.FIELD_NAME) {
            return _parsingContext.getCurrentName();
        }
        if (t.isNumeric()) { // TODO: optimize?
            return getNumberValue().toString();
        }
        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            if (_currToken == JsonToken.VALUE_STRING) {
                return _textBuffer.getTextBuffer();
            }
            if (_currToken == JsonToken.FIELD_NAME) {
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
            }
            if (_currToken.isNumeric()) { // TODO: optimize?
                return getNumberValue().toString().toCharArray();
            }
            return _currToken.asCharArray();
        }
        return null;
    }

    @Override    
    public int getTextLength() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            if (_currToken == JsonToken.VALUE_STRING) {
                return _textBuffer.size();                
            }
            if (_currToken == JsonToken.FIELD_NAME) {
                return _parsingContext.getCurrentName().length();
            }
            if ((_currToken == JsonToken.VALUE_NUMBER_INT)
                    || (_currToken == JsonToken.VALUE_NUMBER_FLOAT)) {
                // TODO: optimize
                return getNumberValue().toString().length();
            }
            return _currToken.asCharArray().length;
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    @Override
    public String getValueAsString() throws IOException
    {
        // inlined 'getText()' for common case of having String
        if (_tokenIncomplete) {
            _tokenIncomplete = false;
            int tb = _typeAsInt;
            int type = (tb >> 5);
            if (type == 2 || type == 3) { // tiny & short ASCII
                return _decodeShortAsciiValue(1 + (tb & 0x3F));
            }
            if (type == 4 || type == 5) { // tiny & short Unicode
                return _decodeShortUnicodeValue(2 + (tb & 0x3F));
            }
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
            return null;
        }
        return getText();
    }

    @Override
    public String getValueAsString(String defaultValue) throws IOException
    {
        if (_currToken != JsonToken.VALUE_STRING) {
            if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
                return defaultValue;
            }
        }
        return getText();
    }

    @Override // since 2.8
    public int getText(Writer writer) throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsToWriter(writer);
        }
        if (t == JsonToken.FIELD_NAME) {
            String n = _parsingContext.getCurrentName();
            writer.write(n);
            return n.length();
        }
        if (t != null) {
            if (t.isNumeric()) {
                return _textBuffer.contentsToWriter(writer);
            }
            char[] ch = t.asCharArray();
            writer.write(ch);
            return ch.length;
        }
        return 0;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, binary
    /**********************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT) {
            // Todo, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject() throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws IOException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            // Todo, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        // Ok, first, unlikely (but legal?) case where someone already requested binary data:
        if (!_tokenIncomplete) {
            if (_binaryValue == null) { // most likely already read...
                return 0;
            }
            final int len = _binaryValue.length;
            out.write(_binaryValue, 0, len);
            return len;
        }

        // otherwise, handle, mark as complete
        // first, raw inlined binary data (simple)
        if (_typeAsInt == SmileConstants.INT_MISC_BINARY_RAW) {
            final int totalCount = _readUnsignedVInt();
            int left = totalCount;
            while (left > 0) {
                int avail = _inputEnd - _inputPtr;
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                    avail = _inputEnd - _inputPtr;
                }
                int count = Math.min(avail, left);
                out.write(_inputBuffer, _inputPtr, count);
                _inputPtr += count;
                left -= count;
            }
            _tokenIncomplete = false;
            return totalCount;
        }
        if (_typeAsInt != SmileConstants.INT_MISC_BINARY_7BIT) {
            _throwInternal();
        }
        // or, alternative, 7-bit encoded stuff:
        final int totalCount = _readUnsignedVInt();
        byte[] encodingBuffer = _ioContext.allocBase64Buffer();
        try {
            _readBinaryEncoded(out, totalCount, encodingBuffer);
        } finally {
            _ioContext.releaseBase64Buffer(encodingBuffer);
        }
        _tokenIncomplete = false;
        return totalCount;
    }

    private void _readBinaryEncoded(OutputStream out, int length, byte[] buffer) throws IOException
    {
        int outPtr = 0;
        final int lastSafeOut = buffer.length - 7;
        // first handle all full 7/8 units
        while (length > 7) {
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
            buffer[outPtr++] = (byte)(i1 >> 24);
            buffer[outPtr++] = (byte)(i1 >> 16);
            buffer[outPtr++] = (byte)(i1 >> 8);
            buffer[outPtr++] = (byte)i1;
            buffer[outPtr++] = (byte)(i2 >> 16);
            buffer[outPtr++] = (byte)(i2 >> 8);
            buffer[outPtr++] = (byte)i2;
            length -= 7;
            // ensure there's always room for at least 7 bytes more after looping:
            if (outPtr > lastSafeOut) {
                out.write(buffer, 0, outPtr);
                outPtr = 0;
            }
        }
        // and then leftovers: n+1 bytes to decode n bytes
        if (length > 0) {
            if ((_inputEnd - _inputPtr) < (length+1)) {
                _loadToHaveAtLeast(length+1);
            }
            int value = _inputBuffer[_inputPtr++];
            for (int i = 1; i < length; ++i) {
                value = (value << 7) + _inputBuffer[_inputPtr++];
                buffer[outPtr++] = (byte) (value >> (7 - i));
            }
            // last byte is different, has remaining 1 - 6 bits, right-aligned
            value <<= length;
            buffer[outPtr++] = (byte) (value + _inputBuffer[_inputPtr++]);
        }
        if (outPtr > 0) {
            out.write(buffer, 0, outPtr);
        }
    }
    
    /*
    /**********************************************************
    /* Internal methods, field name parsing
    /**********************************************************
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
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        // is this needed?
        _typeAsInt = ch;
        switch (ch >> 6) {
        case 0: // misc, including end marker
            switch (ch) {
            case 0x20: // empty String as name, legal if unusual
                _parsingContext.setCurrentName("");
                return JsonToken.FIELD_NAME;
            case 0x30: // long shared
            case 0x31:
            case 0x32:
            case 0x33:
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                }
                {
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
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(_typeAsInt)+" for expected field name (or END_OBJECT marker)");
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
            newShared = Arrays.copyOf(oldShared, newSize);
        }
        return newShared;
    }

    private final String _addDecodedToSymbols(int len, String name)
    {
        if (len < 5) {
            return _symbols.addName(name, _quad1);
        }
        if (len < 9) {
            return _symbols.addName(name, _quad1, _quad2);
        }
        if (len < 13) {
            return _symbols.addName(name, _quad1, _quad2, _quad3);
        }
        int qlen = (len + 3) >> 2;
        return _symbols.addName(name, _quadBuffer, qlen);
    }

    private final String _decodeShortAsciiName(int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        // also note that since it's a short name (64 bytes), segment WILL have enough space
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;

        /* 25-Jan-2014, tsaloranta: Micro-benchmarks suggest that unrolling
         *   does NOT speed up things on JDK 7, let's not do it.
         */
        // loop unrolling seems to help here:
        /*
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
        */
        for (int inEnd = inPtr + len; inPtr < inEnd; ++inPtr) {
            outBuf[outPtr++] = (char) inBuf[inPtr];
        }
        _inputPtr = inPtr;
        return _textBuffer.setCurrentAndReturn(len);
    }
    
    /**
     * Helper method used to decode short Unicode string, length for which actual
     * length (in bytes) is known
     * 
     * @param len Length between 1 and 64
     */
    private final String _decodeShortUnicodeName(int len)
        throws IOException
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
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    // note: slightly edited copy of UTF8StreamParser.addName()
    private final String _decodeLongUnicodeName(int[] quads, int byteLen, int quadLen)
        throws IOException
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
    private final String _findDecodedFromSymbols(final int len) throws IOException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        // First: maybe we already have this name decoded?
        if (len < 5) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            int q = inBuf[inPtr] & 0xFF;
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    if (len > 3) {
                        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q;
            return _symbols.findName(q);
        }

        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;

        // First quadbyte is easy
        int q1 = (inBuf[inPtr++] & 0xFF);
        q1 =  (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 =  (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 =  (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        
        if (len < 9) {
            int q2 = (inBuf[inPtr++] & 0xFF);
            int left = len - 5;
            if (left > 0) {
                q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            return _symbols.findName(q1, q2);
        }

        int q2 = (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);

        if (len < 13) {
            int q3 = (inBuf[inPtr++] & 0xFF);
            int left = len - 9;
            if (left > 0) {
                q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            _quad3 = q3;
            return _symbols.findName(q1, q2, q3);
        }
        return _findDecodedLong(len, q1, q2);
    }

    /**
     * Method for locating names longer than 8 bytes (in UTF-8)
     */
    private final String _findDecodedLong(int len, int q1, int q2) throws IOException
    {
        // first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, bufLen);
            }
        }
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;
        
        // then decode, full quads first
        int offset = 2;
        int inPtr = _inputPtr+8;
        len -= 8;
        
        final byte[] inBuf = _inputBuffer;
        do {
            int q = (inBuf[inPtr++] & 0xFF);
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            _quadBuffer[offset++] = q;
        } while ((len -= 4) > 3);
        // and then leftovers
        if (len > 0) {
            int q = inBuf[inPtr] & 0xFF;
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
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
    /**********************************************************
    /* Internal methods, secondary parsing
    /**********************************************************
     */

    @Override
    protected void _parseNumericValue() throws IOException
    {
        if (!_tokenIncomplete) {
            _reportError("Internal error: number token (%s) decoded, no value set", _currToken);
        }
        _tokenIncomplete = false;
        int tb = _typeAsInt;
	        // ensure we got a numeric type with value that is lazily parsed
        if ((tb >> 5) != 1) {
            _reportError("Current token (%s) not numeric, can not use numeric value accessors", _currToken);
        }
        _finishNumberToken(tb);
    }

    /*
    @Override // since 2.6
    protected int _parseIntValue() throws IOException
    {
        // Inlined variant of: _parseNumericValue(NR_INT)
        if (_tokenIncomplete) {
            _tokenIncomplete = false;
            if ((_typeAsInt & 0x1F) == 4) {
                _finishInt(); // vint
                return _numberInt;
            }
            _finishNumberToken(_typeAsInt);
        }
        if ((_numTypesValid & NR_INT) == 0) {
            convertNumberToInt();
        }
        return _numberInt;
    }
    */

    /**
     * Method called to finish parsing of a token so that token contents
     * are retrievable
     */
    protected final void _finishToken() throws IOException
    {
        _tokenIncomplete = false;
        int tb = _typeAsInt;

        int type = (tb >> 5);
        if (type == 1) { // simple literals, numbers
            _finishNumberToken(tb);
            return;
        }
        if (type <= 3) { // tiny & short ASCII
            _decodeShortAsciiValue(1 + (tb & 0x3F));
            return;
        }
        if (type <= 5) { // tiny & short Unicode
             // short unicode; note, lengths 2 - 65  (off-by-one compared to ASCII)
            _decodeShortUnicodeValue(2 + (tb & 0x3F));
            return;
        }
        if (type == 7) {
            tb &= 0x1F;
            // next 3 bytes define subtype
            switch (tb >> 2) {
            case 0: // long variable length ASCII
            	_decodeLongAscii();
            	return;
            case 1: // long variable length Unicode
            	_decodeLongUnicode();
            	return;
            case 2: // binary, 7-bit
                _binaryValue = _read7BitBinaryWithLength();
                return;
            case 7: // binary, raw
                _finishRawBinary();
                return;
            }
        }
        // sanity check
        _throwInternal();
    }

    protected final void _finishNumberToken(int tb) throws IOException
    {
        switch (tb & 0x1F) {
        case 4:
            _finishInt(); // vint
            return;
        case 5: // vlong
            _finishLong();
            return;
        case 6:
            _finishBigInteger();
            return;
        case 8: // float
            _finishFloat();
            return;
        case 9: // double
            _finishDouble();
            return;
        case 10: // big-decimal
            _finishBigDecimal();
            return;
        }
        _throwInternal();
    }
    
    /*
    /**********************************************************
    /* Internal methods, secondary Number parsing
    /**********************************************************
     */

    private final void _finishInt() throws IOException
    {
        _numTypesValid = NR_INT;
        _numberType = NumberType.INT;
        int ptr = _inputPtr;
        if ((ptr + 5) >= _inputEnd) {
            _finishIntSlow();
            return;
        }
        int value = _inputBuffer[ptr++];
        int i;
        if (value < 0) { // 6 bits
            value &= 0x3F;
        } else {
            i = _inputBuffer[ptr++];
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
            value = (value << 6) + (i & 0x3F);
        }
        _inputPtr = ptr;
        _numberInt = SmileUtil.zigzagDecode(value);
    }
    
    private final void _finishIntSlow() throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int value = _inputBuffer[_inputPtr++];
        int i;
    	    if (value < 0) { // 6 bits
    	        value &= 0x3F;
    	    } else {
    	        if (_inputPtr >= _inputEnd) {
    	            _loadMoreGuaranteed();
    	        }
    	        i = _inputBuffer[_inputPtr++];
    	        if (i >= 0) { // 13 bits
    	            value = (value << 7) + i;
    	            if (_inputPtr >= _inputEnd) {
    	                _loadMoreGuaranteed();
    	            }
    	            i = _inputBuffer[_inputPtr++];
    	            if (i >= 0) {
    	                value = (value << 7) + i;
    	                if (_inputPtr >= _inputEnd) {
    	                    _loadMoreGuaranteed();
    	                }
    	                i = _inputBuffer[_inputPtr++];
    	                if (i >= 0) {
    	                    value = (value << 7) + i;
    	                    // and then we must get negative
    	                    if (_inputPtr >= _inputEnd) {
    	                        _loadMoreGuaranteed();
    	                    }
    	                    i = _inputBuffer[_inputPtr++];
    	                    if (i >= 0) {
    	                        _reportError("Corrupt input; 32-bit VInt extends beyond 5 data bytes");
    	                    }
    	                }
    	            }
    	        }
    	        value = (value << 6) + (i & 0x3F);
    	    }
        _numberInt = SmileUtil.zigzagDecode(value);
    }

    private final void  _finishLong() throws IOException
    {
        _numTypesValid = NR_LONG;
        _numberType = NumberType.LONG;
        int ptr = _inputPtr;
        final int maxEnd = ptr+11;
        if (maxEnd >= _inputEnd) {
            _finishLongSlow();
            return;
        }
        int i = _inputBuffer[ptr++]; // first 7 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 14 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 21
        i = (i << 7) + _inputBuffer[ptr++];

        // Ok: couple of bytes more
        long l = i;
        do {
            int value = _inputBuffer[ptr++];
            if (value < 0) {
                l = (l << 6) + (value & 0x3F);
                _inputPtr = ptr;
                _numberLong = SmileUtil.zigzagDecode(l);
                return;
            }
            l = (l << 7) + value;
        } while (ptr < maxEnd);
        _reportError("Corrupt input; 64-bit VInt extends beyond 11 data bytes");
    }

    private final void  _finishLongSlow() throws IOException
    {
        // Ok, first, will always get 4 full data bytes first; 1 was already passed
        long l = (long) _fourBytesToInt();
        // and loop for the rest
        while (true) {
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            int value = _inputBuffer[_inputPtr++];
            if (value < 0) {
                l = (l << 6) + (value & 0x3F);
                _numberLong = SmileUtil.zigzagDecode(l);
                return;
    	        }
    	        l = (l << 7) + value;
    	    }
    }

    private final int _fourBytesToInt()  throws IOException
    {
        int ptr = _inputPtr;
        if ((ptr + 3) >= _inputEnd) {
            return _fourBytesToIntSlow();
        }
        int i = _inputBuffer[ptr++]; // first 7 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 14 bits
        i = (i << 7) + _inputBuffer[ptr++]; // 21
        i = (i << 7) + _inputBuffer[ptr++];
        _inputPtr = ptr;
        return i;
    }

    private final int _fourBytesToIntSlow()  throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int i = _inputBuffer[_inputPtr++]; // first 7 bits
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        i = (i << 7) + _inputBuffer[_inputPtr++]; // 14 bits
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        i = (i << 7) + _inputBuffer[_inputPtr++]; // 21
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        return (i << 7) + _inputBuffer[_inputPtr++];
    }
    
    private final void _finishBigInteger() throws IOException
    {
        byte[] raw = _read7BitBinaryWithLength();
        _numberBigInt = new BigInteger(raw);
        _numTypesValid = NR_BIGINT;
        _numberType = NumberType.BIG_INTEGER;
    }

    private final void _finishFloat() throws IOException
    {
        // just need 5 bytes to get int32 first; all are unsigned
        int i = _fourBytesToInt();
    	    if (_inputPtr >= _inputEnd) {
    	        _loadMoreGuaranteed();
    	    }
    	    i = (i << 7) + _inputBuffer[_inputPtr++];
    	    float f = Float.intBitsToFloat(i);
    	    _numberFloat = f;
         _numberType = NumberType.FLOAT;
    	    _numTypesValid = NR_FLOAT;
    }

    private final void _finishDouble() throws IOException
    {
        // ok; let's take two sets of 4 bytes (each is int)
        long hi = _fourBytesToInt();
        long value = (hi << 28) + (long) _fourBytesToInt();
        // and then remaining 2 bytes
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        value = (value << 7) + _inputBuffer[_inputPtr++];
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        value = (value << 7) + _inputBuffer[_inputPtr++];
        _numberDouble = Double.longBitsToDouble(value);
        _numberType = NumberType.DOUBLE;
        _numTypesValid = NR_DOUBLE;
    }
	
    private final void _finishBigDecimal() throws IOException
    {
        int scale = SmileUtil.zigzagDecode(_readUnsignedVInt());
        byte[] raw = _read7BitBinaryWithLength();
        _numberBigDecimal = new BigDecimal(new BigInteger(raw), scale);
        _numTypesValid = NR_BIGDECIMAL;
        _numberType = NumberType.BIG_DECIMAL;
    }

    private final int _readUnsignedVInt() throws IOException
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

    private final byte[] _read7BitBinaryWithLength() throws IOException
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
    
    /*
    /**********************************************************
    /* Internal methods, secondary String parsing
    /**********************************************************
     */

    protected final String _decodeShortAsciiValue(int len) throws IOException
    {
        if ((_inputEnd - _inputPtr) < len) {
            _loadToHaveAtLeast(len);
        }
        // Note: we count on fact that buffer must have at least 'len' (<= 64) empty char slots
        final char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;
        
        // as with _decodeShortAsciiName, no unrolling
        for (final int end = inPtr + len; inPtr < end; ++inPtr) {
            outBuf[outPtr++] = (char) inBuf[inPtr];            
        }
        _inputPtr = inPtr;
        return _textBuffer.setCurrentAndReturn(len);
    }

    protected final String _decodeShortUnicodeValue(int len) throws IOException
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
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    private final void _decodeLongAscii() throws IOException
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
    
    /*
    /**********************************************************
    /* Internal methods, skipping
    /**********************************************************
     */

    /**
     * Method called to skip remainders of an incomplete token, when
     * contents themselves will not be needed any more
     */
    protected void _skipIncomplete() throws IOException
    {
        _tokenIncomplete = false;
        int tb = _typeAsInt;
        switch (tb >> 5) {
        case 1: // simple literals, numbers
            tb &= 0x1F;
            // next 3 bytes define subtype
            switch (tb >> 2) {
            case 1: // VInt (zigzag)
                // easy, just skip until we see sign bit... (should we try to limit damage?)
                switch (tb & 0x3) {
                case 1: // vlong
                        _skipBytes(4); // min 5 bytes
                        // fall through
                case 0: // vint
                    while (true) {
                        final int end = _inputEnd;
                        final byte[] buf = _inputBuffer;
                        while (_inputPtr < end) {
                                if (buf[_inputPtr++] < 0) {
                                        return;
                                }
                        }
                        _loadMoreGuaranteed();                           
                    }
                case 2: // big-int
                    // just has binary data
                    _skip7BitBinary();
                    return;
                }
                break;
            case 2: // other numbers
                switch (tb & 0x3) {
                case 0: // float
                    _skipBytes(5);
                    return;
                case 1: // double
                    _skipBytes(10);
                    return;
                case 2: // big-decimal
                    // first, skip scale
                    _readUnsignedVInt();
                    // then length-prefixed binary serialization
                    _skip7BitBinary();
                    return;
                }
                break;
            }
            break;
        case 2: // tiny ASCII
            // fall through
        case 3: // short ASCII
            _skipBytes(1 + (tb & 0x3F));
            return;
        case 4: // tiny unicode
            // fall through
        case 5: // short unicode
            _skipBytes(2 + (tb & 0x3F));
            return;
        case 7:
            tb &= 0x1F;
            // next 3 bytes define subtype
            switch (tb >> 2) {
            case 0: // long variable length ASCII
            case 1: // long variable length unicode
            	/* Doesn't matter which one, just need to find the end marker
            	 * (note: can potentially skip invalid UTF-8 too)
            	 */
            	while (true) {
            	    final int end = _inputEnd;
            	    final byte[] buf = _inputBuffer;
            	    while (_inputPtr < end) {
            	        if (buf[_inputPtr++] == BYTE_MARKER_END_OF_STRING) {
            	            return;
            	        }
            	    }
            	    _loadMoreGuaranteed();
            	}
            	// never gets here
            case 2: // binary, 7-bit
                _skip7BitBinary();
                return;
            case 7: // binary, raw
                _skipBytes(_readUnsignedVInt());
                return;
            }
        }
    	_throwInternal();
    }

    protected void _skipBytes(int len) throws IOException
    {
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            _inputPtr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                return;
            }
            _loadMoreGuaranteed();
        }
    }

    /**
     * Helper method for skipping length-prefixed binary data
     * section
     */
    protected void _skip7BitBinary() throws IOException
    {
        int origBytes = _readUnsignedVInt();
        // Ok; 8 encoded bytes for 7 payload bytes first
        int chunks = origBytes / 7;
        int encBytes = chunks * 8;
        // and for last 0 - 6 bytes, last+1 (except none if no leftovers)
        origBytes -= 7 * chunks;
        if (origBytes > 0) {
            encBytes += 1 + origBytes;
        }
        _skipBytes(encBytes);
    }
    
    /*
    /**********************************************************
    /* Internal methods, UTF8 decoding
    /**********************************************************
     */

    private final int _decodeUtf8_2(int c) throws IOException
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

    private final int _decodeUtf8_3(int c1) throws IOException
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

    private final int _decodeUtf8_3fast(int c1) throws IOException
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

    /**
     * @return Character value <b>minus 0x10000</c>; this so that caller
     *    can readily expand it to actual surrogates
     */
    private final int _decodeUtf8_4(int c) throws IOException
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

        /* note: won't change it to negative here, since caller
         * already knows it'll need a surrogate
         */
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }
    
    /*
    /**********************************************************
    /* Internal methods, error reporting
    /**********************************************************
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
            _reportError("Encountered shared text value reference, even though document header did not declare shared text value references may be included");
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
	
    protected void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    /*
    /**********************************************************
    /* Internal methods, other
    /**********************************************************
     */

    private final JsonToken _eofAsNextToken() throws IOException {
        if (!_parsingContext.inRoot()) {
            _handleEOF();
        }
        close();
        return (_currToken = null);
    }
}

