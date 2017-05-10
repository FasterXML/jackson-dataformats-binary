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

public abstract class NonBlockingParserBase<F extends NonBlockingInputFeeder>
    extends ParserBase
    implements NonBlockingParser<F>
{
    protected final static byte[] NO_BYTES = new byte[0];
    protected final static int[] NO_INTS = new int[0];
    protected final static String[] NO_STRINGS = new String[0];

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

    protected final static int MINOR_HEADER = 1;

    protected final static int MINOR_FIELD_NAME_2BYTE = 2;

    protected final static int MINOR_FIELD_NAME_LONG = 3;
    protected final static int MINOR_FIELD_NAME_SHORT_ASCII = 4;
    protected final static int MINOR_FIELD_NAME_SHORT_UNICODE = 5;

    protected final static int MINOR_VALUE_NUMBER_INT = 6;
    protected final static int MINOR_VALUE_NUMBER_LONG = 7;
    protected final static int MINOR_VALUE_NUMBER_FLOAT = 8;
    protected final static int MINOR_VALUE_NUMBER_DOUBLE = 9;
    protected final static int MINOR_VALUE_NUMBER_BIGINT = 10;
    protected final static int MINOR_VALUE_NUMBER_BIGDEC = 11;

    protected final static int MINOR_VALUE_STRING_SHORT_ASCII = 12;
    protected final static int MINOR_VALUE_STRING_SHORT_UNICODE = 13;
    protected final static int MINOR_VALUE_STRING_LONG_ASCII = 14;
    protected final static int MINOR_VALUE_STRING_LONG_UNICODE = 15;
    protected final static int MINOR_VALUE_STRING_SHARED_2BYTE = 16;

    protected final static int MINOR_VALUE_BINARY_RAW = 17;
    protected final static int MINOR_VALUE_BINARY_7BIT = 18;
    
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

    protected float _numberFloat;

    /**
     * Temporary storage for 32-bit values (int, float), as well as length markers
     * for length-prefixed values.
     */
    protected int _pending32;

    /**
     * For 64-bit values, we may use this for combining values
     */
    protected long _pending64;

    protected NumberType _numberType;

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
        // We don't need a lot; for most things maximum known a-priori length below 70 bytes
        _inputCopy = ctxt.allocReadIOBuffer(500);
        
        _tokenInputRow = -1;
        _tokenInputCol = -1;
        _smileBufferRecycler = _smileBufferRecycler();

        _currToken = null;
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
    /**********************************************************
    /* Overridden methods
    /**********************************************************
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
            byte[] b = _inputCopy;
            if (b != null) {
                _inputCopy = null;
                _ioContext.releaseReadIOBuffer(b);
            }
        }
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

    @Override
    public String getCurrentName() throws IOException
    {
        return _parsingContext.getCurrentName();
    }

    /*
    /**********************************************************************
    /* NonBlockParser impl (except for NonBlockingInputFeeder)
    /**********************************************************************
     */

    @Override
    public abstract F getInputFeeder();

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
        /*
        if (_tokenIncomplete) {
            return null;
        }
        */
        JsonToken t = _currToken;
        if (t == null || _currToken == JsonToken.NOT_AVAILABLE) { // null only before/after document
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
            case NOT_AVAILABLE:
                return null;
            default:
/*                
                if (_tokenIncomplete) {
                    return null;
                }
 */
                return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override    
    public int getTextLength() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            /*
            if (_tokenIncomplete) {
                return -1; // or throw exception?
            }
            */
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
                
            case NOT_AVAILABLE:
                return 0;
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
    public NumberType getNumberType() throws IOException {
        _checkNumericValue();
        return _numberType;
    }

    @Override
    public float getFloatValue() throws IOException
    {
        if ((_numTypesValid & NR_FLOAT) == 0) {
            _checkNumericValue();
            convertNumberToFloat();
        }
        return _numberFloat;
    }

    protected final void convertNumberToFloat() throws IOException
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
    
    protected final void _checkNumericValue() throws IOException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token ("+_currToken+") not numeric, can not use numeric value accessors");
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

    // Helper method for trying to find specified encoded UTF-8 byte sequence
    // from symbol table; if successful avoids actual decoding to String
    protected final String _findDecodedFromSymbols(byte[] inBuf, int inPtr, int len) throws IOException
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
        return _findDecodedMedium(inBuf, inPtr, len);
    }
    
    // Method for locating names longer than 8 bytes (in UTF-8)
    private final String _findDecodedMedium(byte[] inBuf, int inPtr, int len) throws IOException
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
    protected final JsonToken _eofAsNextToken() throws IOException {
        _majorState = MAJOR_CLOSED;
        if (!_parsingContext.inRoot()) {
            _handleEOF();
        }
        close();
        return (_currToken = null);
    }

    protected final JsonToken _valueComplete(JsonToken t) throws IOException
    {
        _majorState = _majorStateAfterValue;
        _currToken = t;
        return t;
    }

    protected final JsonToken _handleSharedString(int index) throws IOException
    {
        if (index >= _seenStringValueCount) {
            _reportInvalidSharedStringValue(index);
        }
        _textBuffer.resetWithString(_seenStringValues[index]);
        return _valueComplete(JsonToken.VALUE_STRING);
        
    }

    protected final void _addSeenStringValue(String v) throws IOException
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
