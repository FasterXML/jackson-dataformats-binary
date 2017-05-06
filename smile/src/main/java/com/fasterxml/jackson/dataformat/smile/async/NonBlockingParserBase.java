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

public abstract class NonBlockingParserBase
    extends ParserBase
    implements NonBlockingParser<NonBlockingByteArrayFeeder>,
        NonBlockingByteArrayFeeder
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

    /**
     * State for recognized header marker, either in-feed or initial, and at least
     * one byte of it has been read.
     */
    protected final static int MAJOR_HEADER = 2;

    protected final static int MAJOR_OBJECT_START = 3;
    protected final static int MAJOR_OBJECT_FIELD = 4;
    protected final static int MAJOR_OBJECT_VALUE = 5;

    protected final static int MAJOR_ARRAY_START = 6;
    protected final static int MAJOR_ARRAY_ELEMENT = 7;

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

    // // // "Sub-states"

    // Need 2nd byte of 2-byte field back reference
    protected final static int MINOR_FIELD_NAME_2BYTE = 1;

    protected final static int MINOR_FIELD_NAME_LONG = 2;
    protected final static int MINOR_FIELD_NAME_SHORT_ASCII = 3;
    protected final static int MINOR_FIELD_NAME_SHORT_UNICODE = 4;
    
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
//        _finishToken();
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
    /* Internal methods, secondary parsing
    /**********************************************************************
     */

    @Override
    protected void _parseNumericValue(int expType) throws IOException
    {
//        if (_tokenIncomplete) {
            _reportError("No current token available, can not call accessors");
//        }
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
