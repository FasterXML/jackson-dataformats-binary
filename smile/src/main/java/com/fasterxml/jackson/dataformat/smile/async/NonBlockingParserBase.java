package com.fasterxml.jackson.dataformat.smile.async;

import java.io.*;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.dataformat.smile.*;

public abstract class NonBlockingParserBase
    extends SmileParserBase
{
    /*
    /**********************************************************************
    /* Major state constants
    /**********************************************************************
     */

    /**
     * State right after parser has been constructed, before seeing the first byte
     * to know if there's header.
     */
    protected final static int MAJOR_INITIAL = 0;

    /**
     * State right after parser a root value has been
     * finished, but next token has not yet been recognized.
     */
    protected final static int MAJOR_ROOT = 1;

    protected final static int MAJOR_OBJECT_FIELD = 2;
    protected final static int MAJOR_OBJECT_VALUE = 3;

    protected final static int MAJOR_ARRAY_ELEMENT = 4;

    /**
     * State after non-blocking input source has indicated that no more input
     * is forthcoming AND we have exhausted all the input
     */
    protected final static int MAJOR_CLOSED = 5;
    
    // // // "Sub-states"

    protected final static int MINOR_HEADER_INITIAL = 1;
    protected final static int MINOR_HEADER_INLINE = 2;

    protected final static int MINOR_FIELD_NAME_2BYTE = 3;

    protected final static int MINOR_FIELD_NAME_LONG = 4;
    protected final static int MINOR_FIELD_NAME_SHORT_ASCII = 5;
    protected final static int MINOR_FIELD_NAME_SHORT_UNICODE = 6;

    protected final static int MINOR_VALUE_NUMBER_INT = 7;
    protected final static int MINOR_VALUE_NUMBER_LONG = 8;
    protected final static int MINOR_VALUE_NUMBER_FLOAT = 9;
    protected final static int MINOR_VALUE_NUMBER_DOUBLE = 10;

    protected final static int MINOR_VALUE_NUMBER_BIGINT_LEN = 11;
    protected final static int MINOR_VALUE_NUMBER_BIGINT_BODY = 12;
    protected final static int MINOR_VALUE_NUMBER_BIGDEC_SCALE = 13;
    protected final static int MINOR_VALUE_NUMBER_BIGDEC_LEN = 14;
    protected final static int MINOR_VALUE_NUMBER_BIGDEC_BODY = 15;

    protected final static int MINOR_VALUE_STRING_SHORT_ASCII = 16;
    protected final static int MINOR_VALUE_STRING_SHORT_UNICODE = 17;
    protected final static int MINOR_VALUE_STRING_LONG_ASCII = 18;
    protected final static int MINOR_VALUE_STRING_LONG_UNICODE = 19;
    protected final static int MINOR_VALUE_STRING_SHARED_2BYTE = 20;

    protected final static int MINOR_VALUE_BINARY_RAW_LEN = 21;
    protected final static int MINOR_VALUE_BINARY_RAW_BODY = 22;

    protected final static int MINOR_VALUE_BINARY_7BIT_LEN = 23;
    protected final static int MINOR_VALUE_BINARY_7BIT_BODY = 24;

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
     * Value of {@link #_majorState} after completing a scalar value
     */
    protected int _majorStateAfterValue;

    /**
     * Flag that is sent when calling application indicates that there will
     * be no more input to parse.
     */
    protected boolean _endOfInput = false;

    /*
    /**********************************************************************
    /* Other buffering
    /**********************************************************************
     */
    
    /**
     * Temporary buffer for holding content if input not contiguous (but can
     * fit in buffer)
     */
    protected byte[] _inputCopy;

    /**
     * Number of bytes buffered in <code>_inputCopy</code>
     */
    protected int _inputCopyLen;

    /**
     * Temporary storage for 32-bit values (int, float), as well as length markers
     * for length-prefixed values.
     */
    protected int _pending32;

    /**
     * Temporary storage for 64-bit values (long, double), secondary storage
     * for some other things (scale of BigDecimal values)
     */
    protected long _pending64;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public NonBlockingParserBase(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, int smileFeatures,
            ByteQuadsCanonicalizer sym)
    {
        super(readCtxt, ioCtxt, parserFeatures, smileFeatures, sym);
        // We don't need a lot; for most things maximum known a-priori length below 70 bytes
        _inputCopy = ioCtxt.allocReadIOBuffer(500);

        _currToken = null;
        _majorState = MAJOR_INITIAL;
    }

    @Override
    public boolean canParseAsync() { return true; }

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
    @Override
    protected void _releaseBuffers2()
    {
        byte[] b = _inputCopy;
        if (b != null) {
            _inputCopy = null;
            _ioContext.releaseReadIOBuffer(b);
        }
    }

    /*
    /**********************************************************
    /* Test support
    /**********************************************************
     */

    protected ByteQuadsCanonicalizer symbolTableForTests() {
        return _symbols;
    }

    /*
    /**********************************************************
    /* Abstract methods from JsonParser
    /**********************************************************
     */

    @Override
    public abstract int releaseBuffered(OutputStream out);

    @Override
    public Object getInputSource() {
        // since input is "pushed", to traditional source...
        return null;
    }

    @Override
    protected void _closeInput() {
        // nothing to do here
    }

    /*
    /**********************************************************************
    /* Abstract methods from SmileParserBase
    /**********************************************************************
     */

    // No incomplete values yet -- although may want to make BigInt/BigDec lazy
    // in future
    @Override
    protected void _parseNumericValue() throws JacksonException {
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token (%s) not numeric, can not use numeric value accessors", _currToken);
    }

    /*
    /**********************************************************************
    /* Overridden methods
    /**********************************************************************
     */

    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            // yes; is or can be made available efficiently as char[]
            return _textBuffer.hasTextAsCharacters();
        }
        // other types, no benefit from accessing as char[]
        return false;
    }

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
    public String getText() throws JacksonException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        JsonToken t = _currToken;
        if (t == null || _currToken == JsonToken.NOT_AVAILABLE) { // null only before/after document
            return null;
        }
        if (t == JsonToken.FIELD_NAME) {
            return _parsingContext.currentName();
        }
        if (t.isNumeric()) {
            // TODO: optimize?
            return getNumberValue().toString();
        }
        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws JacksonException
    {
        switch (currentTokenId()) {
        case JsonTokenId.ID_STRING:
            return _textBuffer.getTextBuffer();
        case JsonTokenId.ID_FIELD_NAME:
            return _parsingContext.currentName().toCharArray();
        case JsonTokenId.ID_NUMBER_INT:
        case JsonTokenId.ID_NUMBER_FLOAT:
            return getNumberValue().toString().toCharArray();
        case JsonTokenId.ID_NO_TOKEN:
        case JsonTokenId.ID_NOT_AVAILABLE:
            return null;
        default:
            return _currToken.asCharArray();
        }
    }

    @Override    
    public int getTextLength() throws JacksonException
    {
        switch (currentTokenId()) {
        case JsonTokenId.ID_STRING:
            return _textBuffer.size();
        case JsonTokenId.ID_FIELD_NAME:
            return _parsingContext.currentName().length();
        case JsonTokenId.ID_NUMBER_INT:
        case JsonTokenId.ID_NUMBER_FLOAT:
            return getNumberValue().toString().length();
        case JsonTokenId.ID_NO_TOKEN:
        case JsonTokenId.ID_NOT_AVAILABLE:
            return 0; // or throw exception?
        default:
            return _currToken.asCharArray().length;
        }
    }

    @Override
    public int getTextOffset() throws JacksonException {
        return 0;
    }

    @Override
    public int getText(Writer w) throws JacksonException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            try {
                return _textBuffer.contentsToWriter(w);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
        if (_currToken == JsonToken.NOT_AVAILABLE) {
            _reportError("Current token not available: can not call this method");
        }
        // otherwise default handling works fine
        return super.getText(w);
    }

    /*
    /**********************************************************************
    /* Public API, access to token information, binary
    /**********************************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws JacksonException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            _reportError("Current token (%s) not VALUE_EMBEDDED_OBJECT, can not access as binary", _currToken);
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject() throws JacksonException
    {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out)
            throws JacksonException {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            _reportError("Current token (%s) not VALUE_EMBEDDED_OBJECT, can not access as binary", _currToken);
        }
        try {
            out.write(_binaryValue);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return _binaryValue.length;
    }

    /*
    /**********************************************************************
    /* Handling of nested scope, state
    /**********************************************************************
     */

    protected final JsonToken _startArrayScope() throws JacksonException
    {
        _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
        _majorState = MAJOR_ARRAY_ELEMENT;
        _majorStateAfterValue = MAJOR_ARRAY_ELEMENT;
        return (_currToken = JsonToken.START_ARRAY);
    }

    protected final JsonToken _startObjectScope() throws JacksonException
    {
        _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
        _majorState = MAJOR_OBJECT_FIELD;
        _majorStateAfterValue = MAJOR_OBJECT_FIELD;
        return (_currToken = JsonToken.START_OBJECT);
    }

    protected final JsonToken _closeArrayScope() throws JacksonException
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

    protected final JsonToken _closeObjectScope() throws JacksonException
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
    /* Internal methods, field name parsing
    /**********************************************************************
     */

    // Helper method for trying to find specified encoded UTF-8 byte sequence
    // from symbol table; if successful avoids actual decoding to String
    protected final String _findDecodedFromSymbols(byte[] inBuf, int inPtr, int len) throws JacksonException
    {
        // First: maybe we already have this name decoded?
        if (len < 5) {
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
        return _findDecodedLonger(inBuf, inPtr, len);
    }
    
    // Method for locating names longer than 8 bytes (in UTF-8)
    private final String _findDecodedLonger(byte[] inBuf, int inPtr, int len) throws JacksonException
    {
        // first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = Arrays.copyOf(_quadBuffer, bufLen+4);
            }
        }
        // then decode, full quads first
        int offset = 0;
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

    protected final String _addDecodedToSymbols(int len, String name)
    {
        if (len < 5) {
            return _symbols.addName(name, _quad1);
        }
        if (len < 9) {
            return _symbols.addName(name, _quad1, _quad2);
        }
        int qlen = (len + 3) >> 2;
        return _symbols.addName(name, _quadBuffer, qlen);
    }
    
    /**
     * Method called to try to expand shared name area to fit one more potentially
     * shared String. If area is already at its biggest size, will just clear
     * the area (by setting next-offset to 0)
     */
    protected final String[] _expandSeenNames(String[] oldShared)
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

    /*
    /**********************************************************************
    /* Internal methods, state changes
    /**********************************************************************
     */
    
    /**
     * Helper method called at point when all input has been exhausted and
     * input feeder has indicated no more input will be forthcoming.
     */
    protected final JsonToken _eofAsNextToken() throws JacksonException {
        _majorState = MAJOR_CLOSED;
        if (!_parsingContext.inRoot()) {
            _handleEOF();
        }
        close();
        return (_currToken = null);
    }

    protected final JsonToken _valueComplete(JsonToken t) throws JacksonException
    {
        _majorState = _majorStateAfterValue;
        _currToken = t;
        return t;
    }

    protected final JsonToken _handleSharedString(int index) throws JacksonException
    {
        if (index >= _seenStringValueCount) {
            _reportInvalidSharedStringValue(index);
        }
        _textBuffer.resetWithString(_seenStringValues[index]);
        return _valueComplete(JsonToken.VALUE_STRING);
    }

    protected final JsonToken _handleSharedName(int index) throws JacksonException
    {
        if (index >= _seenNameCount) {
            _reportInvalidSharedName(index);
        }
        _parsingContext.setCurrentName(_seenNames[index]);
        _majorState = MAJOR_OBJECT_VALUE;
        return (_currToken = JsonToken.FIELD_NAME);
    }

    protected final void _addSeenStringValue(String v) throws JacksonException
    {
        if (_seenStringValueCount < _seenStringValues.length) {
            _seenStringValues[_seenStringValueCount++] = v;
            return;
        }
        _expandSeenStringValues(v);
    }

    private final void _expandSeenStringValues(String v)
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
        _seenStringValues[_seenStringValueCount++] = v;
    }

    /*
    /**********************************************************
    /* Internal/package methods: shared/reusable builders
    /**********************************************************
     */

    /**
     * ByteArrayBuilder is needed if 'getBinaryValue' is called. If so,
     * we better reuse it for remainder of content.
     */
    protected ByteArrayBuilder _byteArrayBuilder = null;

    public void _initByteArrayBuilder()
    {
        if (_byteArrayBuilder == null) {
            _byteArrayBuilder = new ByteArrayBuilder();
        } else {
            _byteArrayBuilder.reset();
        }
    }

    /*
    /**********************************************************************
    /* Internal methods, error reporting
    /**********************************************************************
     */

    protected void _reportMissingHeader(int unmaskedFirstByte) throws JacksonException
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

    protected void _reportInvalidSharedName(int index) throws JacksonException
    {
        if (_seenNames == null) {
            _reportError("Encountered shared name reference, even though document header explicitly declared no shared name references are included");
        }
       _reportError("Invalid shared name reference %d; only got %d names in buffer (invalid content)",
               index, _seenNameCount);
    }

    protected void _reportInvalidSharedStringValue(int index) throws JacksonException
    {
        if (_seenStringValues == null) {
            _reportError("Encountered shared text value reference, even though document header did not declare shared text value references may be included");
        }
       _reportError("Invalid shared text value reference %d; only got %s names in buffer (invalid content)",
               index, _seenStringValueCount);
    }

    protected void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }
	
    protected void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }
}
