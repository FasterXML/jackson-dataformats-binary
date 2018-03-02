package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;

/**
 * Implementation class that exposes additional internal API
 * to be used as callbacks by {@link AvroReadContext} implementations.
 */
public class JacksonAvroParserImpl extends AvroParserImpl
{
    /**
     * Additionally we can combine UTF-8 decoding info into similar
     * data table.
     * Values indicate "byte length - 1"; meaning -1 is used for
     * invalid bytes, 0 for single-byte codes, 1 for 2-byte codes
     * and 2 for 3-byte codes.
     */
    public final static int[] sUtf8UnitLengths;
    static {
        int[] table = new int[256];
        for (int c = 128; c < 256; ++c) {
            int code;

            // We'll add number of bytes needed for decoding
            if ((c & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                code = 1;
            } else if ((c & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                code = 2;
            } else if ((c & 0xF8) == 0xF0) {
                // 4 bytes; double-char with surrogates and all...
                code = 3;
            } else {
                // And -1 seems like a good "universal" error marker...
                code = -1;
            }
            table[c] = code;
        }
        sUtf8UnitLengths = table;
    }
    
    /*
    /**********************************************************
    /* Input source config
    /**********************************************************
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
    /* Life-cycle
    /**********************************************************
     */
    
    public JacksonAvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec, InputStream in)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputPtr = 0;
        _inputEnd = 0;
        _bufferRecyclable = true;
    }

    public JacksonAvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec,
            byte[] data, int offset, int len)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = null;
        _inputBuffer = data;
        _inputPtr = offset;
        _inputEnd = offset + len;
    }

    @Override
    protected void _releaseBuffers() throws IOException {
        super._releaseBuffers();
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
    /* Abstract method impls, i/o access
    /**********************************************************
     */

    @Override
    public Object getInputSource() {
        return _inputStream;
    }

    @Override
    protected void _closeInput() throws IOException {
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

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

    /*
    /**********************************************************
    /* Abstract method impls, traversal
    /**********************************************************
     */

    // !!! TODO: optimize
    @Override
    public String nextTextValue() throws IOException {
        if (nextToken() == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        return null;
    }

    /*
    /**********************************************************
    /* Abstract method impls, text
    /**********************************************************
     */

    @Override
    public boolean hasTextCharacters() {
        if (_currToken == JsonToken.VALUE_STRING) { return true; } // usually true
        // name might be copied but...
        return false;
    }

    @Override
    public String getText() throws IOException
    {
        JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsAsString();
        }
        if (t == JsonToken.FIELD_NAME) {
            return _avroContext.getCurrentName();
        }
        if (t != null) {
            if (t.isNumeric()) {
                return getNumberValue().toString();
            }
            return _currToken.asString();
        }
        return null;
    }

    @Override // since 2.8
    public int getText(Writer writer) throws IOException
    {
        JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            return _textBuffer.contentsToWriter(writer);
        }
        if (t == JsonToken.FIELD_NAME) {
            String n = _avroContext.getCurrentName();
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
    /* Methods for AvroReadContext implementations: decoding int
    /**********************************************************
     */

    @Override
    public JsonToken decodeIntToken() throws IOException {
        _numberInt = decodeInt();
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }

    @Override
    public final int decodeInt() throws IOException
    {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 5) {
            return _decodeIntSlow();
        }
        final byte[] buf = _inputBuffer;
        int b = buf[ptr++];
        int i = b & 0x7F;
        if (b < 0) {
            b = buf[ptr++];
            i += ((b & 0x7F) << 7);
            if (b < 0) {
                b = buf[ptr++];
                i += ((b & 0x7F) << 14);
                if (b < 0) {
                    b = buf[ptr++];
                    i += ((b & 0x7F) << 21);
                    if (b < 0) {
                        b = buf[ptr++];
                        if (b < 0) {
                            _inputPtr = ptr;
                            _reportInvalidNegative(b);
                        }
                        i += (b << 28);
                    }
                }
            }
        }
        _inputPtr = ptr;
        // and final part: Zigzag decode
        return (i >>> 1) ^ (-(i & 1));
    }

    public int _decodeIntSlow() throws IOException {
        int b = _nextByteGuaranteed();
        int i = b & 0x7F;
        if (b < 0) {
            b = _nextByteGuaranteed();
            i += ((b & 0x7F) << 7);
            if (b < 0) {
                b = _nextByteGuaranteed();
                i += ((b & 0x7F) << 14);
                if (b < 0) {
                    b = _nextByteGuaranteed();
                    i += ((b & 0x7F) << 21);
                    if (b < 0) {
                        b = _nextByteGuaranteed();
                        if (b < 0) {
                            _reportInvalidNegative(b);
                        }
                        i += (b << 28);
                    }
                }
            }
        }
        // and final part: Zigzag decode
        return (i >>> 1) ^ (-(i & 1));
    }

    @Override
    public void skipInt() throws IOException
    {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 5) {
            _skipIntSlow();
            return;
        }
        final byte[] buf = _inputBuffer;
        if (buf[ptr++] < 0) {
            if (buf[ptr++] < 0) {
                if (buf[ptr++] < 0) {
                    if (buf[ptr++] < 0) {
                        int b = buf[ptr++];
                        if (b < 0) {
                            _inputPtr = ptr;
                            _reportInvalidNegative(b);
                        }
                    }
                }
            }
        }
        _inputPtr = ptr;
    }

    public void _skipIntSlow() throws IOException {
        if (_nextByteGuaranteed() < 0) {
            if (_nextByteGuaranteed() < 0) {
                if (_nextByteGuaranteed() < 0) {
                    if (_nextByteGuaranteed() < 0) {
                        int b = _nextByteGuaranteed();
                        if (b < 0) {
                            _reportInvalidNegative(b);
                        }
                    }
                }
            }
        }
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding long
    /**********************************************************
     */

    @Override
    public JsonToken decodeLongToken() throws IOException {
        _numberLong = decodeLong();
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    @Override
    public long decodeLong() throws IOException {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 10) {
            return _decodeLongSlow();
        }
        final byte[] buf = _inputBuffer;
        // inline handling of first 4 bytes (for 28-bits of content)
        int b = buf[ptr++];
        int i = b & 0x7F;
        if (b < 0) {
            b = buf[ptr++];
            i += ((b & 0x7F) << 7);
            if (b < 0) {
                b = buf[ptr++];
                i += ((b & 0x7F) << 14);
                if (b < 0) {
                    b = buf[ptr++];
                    i += ((b & 0x7F) << 21);
                    if (b < 0) {
                        return _decodeLong2(ptr, i);
                    }
                }
            }
        }
        _inputPtr = ptr;
        // should be ok to zigzag as int, then sign-extend
        i = (i >>> 1) ^ (-(i & 1));
        return (long) i;
    }

    private long _decodeLong2(int ptr, long lo) throws IOException
    {
        final byte[] buf = _inputBuffer;
        // then next 28 bits (altogether 8 bytes)
        int b = buf[ptr++];
        int i = b & 0x7F;
        if (b < 0) {
            b = buf[ptr++];
            i += ((b & 0x7F) << 7);
            if (b < 0) {
                b = buf[ptr++];
                i += ((b & 0x7F) << 14);
                if (b < 0) {
                    b = buf[ptr++];
                    i += ((b & 0x7F) << 21);
                    if (b < 0) {
                        // Ok 56-bits gone... still going strong!
                        b = buf[ptr++];
                        int inner = b & 0x7F;
                        if (b < 0) {
                            b = buf[ptr++];
                            if (b < 0) {
                                _inputPtr = ptr;
                                _reportInvalidNegative(b);
                            }
                            inner |= ((b & 0x1) << 7);
                        }
                        lo |= (((long) inner) << 56);
                    }
                }
            }
        }
        _inputPtr = ptr;
        lo |= (((long) i) << 28);
        return (lo >>> 1) ^ (-(lo & 1));
    }

    public long _decodeLongSlow() throws IOException {
        int b = _nextByteGuaranteed();
        int i = b & 0x7F;
        if (b < 0) {
            b = _nextByteGuaranteed();
            i += ((b & 0x7F) << 7);
            if (b < 0) {
                b = _nextByteGuaranteed();
                i += ((b & 0x7F) << 14);
                if (b < 0) {
                    b = _nextByteGuaranteed();
                    i += ((b & 0x7F) << 21);
                    if (b < 0) {
                        return _decodeLongSlow2(i);
                    }
                }
            }
        }
        i = (i >>> 1) ^ (-(i & 1));
        return (long) i;
    }

    private long _decodeLongSlow2(long lo) throws IOException
    {
        // then next 28 bits (altogether 8 bytes)
        int b = _nextByteGuaranteed();
        int i = b & 0x7F;
        if (b < 0) {
            i &= 0x7F;
            b = _nextByteGuaranteed();
            i += ((b & 0x7F) << 7);
            if (b < 0) {
                b = _nextByteGuaranteed();
                i += ((b & 0x7F) << 14);
                if (b < 0) {
                    b = _nextByteGuaranteed();
                    i += ((b & 0x7F) << 21);
                    if (b < 0) {
                        // Ok 56-bits gone... still going strong!
                        lo |= (((long) i) << 28);
                        b = _nextByteGuaranteed();
                        i = b & 0x7F;
                        if (b < 0) {
                            b = _nextByteGuaranteed();
                            if (i < 0) {
                                _reportInvalidNegative(b);
                            }
                            i |= (b << 7);
                        }
                        lo |= (((long) i) << 56);
                        return (lo >>> 1) ^ (-(lo & 1));
                    }
                }
            }
        }
        lo |= (((long) i) << 28);
        return (lo >>> 1) ^ (-(lo & 1));
    }

    @Override
    public void skipLong() throws IOException {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 10) {
            _skipLongSlow();
            return;
        }
        final byte[] buf = _inputBuffer;
        if (buf[ptr++] < 0) {
            int maxLeft = 9;
            int b;
            do {
                b = _nextByteGuaranteed();
            } while ((--maxLeft > 0) && (b < 0));
            if (b < 0) {
                _reportInvalidNegative(b);
            }
        }
        _inputPtr = ptr;
    }

    public void _skipLongSlow() throws IOException {
        if (_nextByteGuaranteed() < 0) {
            int maxLeft = 9;
            int b;
            do {
                b = _nextByteGuaranteed();
            } while ((--maxLeft > 0) && (b < 0));
            if (b < 0) {
                _reportInvalidNegative(b);
            }
        }
    }
    
    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding float/double
    /**********************************************************
     */

    @Override
    public JsonToken decodeFloat() throws IOException {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 4) {
            _loadToHaveAtLeast(4);
            ptr = _inputPtr;
        }
        final byte[] buf = _inputBuffer;
        _inputPtr = ptr+4;
        int i = (buf[ptr] & 0xff) | ((buf[ptr+1] & 0xff) << 8)
                | ((buf[ptr+2] & 0xff) << 16) | (buf[ptr+3] << 24);
        _numberFloat = Float.intBitsToFloat(i);
        _numTypesValid = NR_FLOAT;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    @Override
    public void skipFloat() throws IOException {
        _skip(4);
    }

    @Override
    public JsonToken decodeDouble() throws IOException {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 8) {
            _loadToHaveAtLeast(8);
            ptr = _inputPtr;
        }
        final byte[] buf = _inputBuffer;
        int i = (buf[ptr] & 0xff) | ((buf[ptr+1] & 0xff) << 8)
                | ((buf[ptr+2] & 0xff) << 16) | (buf[ptr+3] << 24);
        ptr += 4;
        int i2 = (buf[ptr] & 0xff) | ((buf[ptr+1] & 0xff) << 8)
                | ((buf[ptr+2] & 0xff) << 16) | (buf[ptr+3] << 24);

        _inputPtr = ptr+4;
        _numberDouble = Double.longBitsToDouble((((long) i) & 0xffffffffL)
                | (((long) i2) << 32));
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    @Override
    public void skipDouble() throws IOException {
        _skip(8);
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Strings
    /**********************************************************
     */

    @Override
    public JsonToken decodeStringToken() throws IOException {
        decodeString();
        return JsonToken.VALUE_STRING;
    }

    @Override
    public void decodeString() throws IOException {
        int len = decodeInt();
        if (len <= 0) {
            if (len < 0) {
                _reportError("Invalid length indicator for String: "+len);
            }
            _textBuffer.resetWithEmpty();
            return;
        }

        if (len > (_inputEnd - _inputPtr)) {
            // or if not, could we read?
            if (len >= _inputBuffer.length) {
                // If not enough space, need handling similar to chunked
                _finishLongText(len);
                return;
            }
            _loadToHaveAtLeast(len);
        }
        // offline for better optimization
        _finishShortText(len);
    }

    @Override
    public void skipString() throws IOException {
        int len = decodeInt();
        if (len <= 0) {
            if (len < 0) {
                _reportError("Invalid length indicator for String: "+len);
            }
            return;
        }
        _skip(len);
    }

    private final String _finishShortText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }
        
        int outPtr = 0;
        int inPtr = _inputPtr;
        _inputPtr += len;
        final byte[] inputBuf = _inputBuffer;

        // Let's actually do a tight loop for ASCII first:
        final int end = inPtr + len;

        int i;
        while ((i = inputBuf[inPtr]) >= 0) {
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                return _textBuffer.setCurrentAndReturn(outPtr);
            }
        }

        final int[] codes = sUtf8UnitLengths;
        do {
            i = inputBuf[inPtr++] & 0xFF;
            switch (codes[i]) {
            case 0:
                break;
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
                _reportError("Invalid byte "+Integer.toHexString(i)+" in Unicode text block");
            }
            outBuf[outPtr++] = (char) i;
        } while (inPtr < end);
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    private final void _finishLongText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = sUtf8UnitLengths;
        int outEnd = outBuf.length;

        while (--len >= 0) {
            int c = _nextByteGuaranteed() & 0xFF;
            int code = codes[c];
            if (code == 0 && outPtr < outEnd) {
                outBuf[outPtr++] = (char) c;
                continue;
            }
            if ((len -= code) < 0) { // may need to improve error here but...
                throw _constructError("Malformed UTF-8 character at end of long (non-chunked) text segment");
            }
            
            switch (code) {
            case 0:
                break;
            case 1: // 2-byte UTF
                {
                    int d = _nextByteGuaranteed();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF, _inputPtr);
                    }
                    c = ((c & 0x1F) << 6) | (d & 0x3F);
                }
                break;
            case 2: // 3-byte UTF
                c = _decodeUTF8_3(c);
                break;
            case 3: // 4-byte UTF
                c = _decodeUTF8_4(c);
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outEnd) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
                outEnd = outBuf.length;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    private final int _decodeUTF8_3(int c1) throws IOException
    {
        c1 &= 0x0F;
        int d = _nextByteGuaranteed();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = _nextByteGuaranteed();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private final int _decodeUTF8_4(int c) throws IOException
    {
        int d = _nextByteGuaranteed();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = _nextByteGuaranteed();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = _nextByteGuaranteed();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    protected void _reportInvalidChar(int c) throws JsonParseException {
        // Either invalid WS or illegal UTF-8 start char
        if (c < ' ') {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }

    private void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }

    private void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }

    private void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }
    
    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Bytes
    /**********************************************************
     */

    @Override
    public JsonToken decodeBytes() throws IOException {
        int len = decodeInt();
        if (len <= 0) {
            if (len < 0) {
                _reportError("Invalid length indicator for Bytes: "+len);
            }
            _binaryValue = NO_BYTES;
        } else {
            byte[] b = new byte[len];
            // this is simple raw read, safe to use:
            _read(b, 0, len);
            _binaryValue = b;
        }
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    @Override
    public void skipBytes() throws IOException {
        int len = decodeInt();
        if (len <= 0) {
            if (len < 0) {
                _reportError("Invalid length indicator for Bytes: "+len);
            }
            _binaryValue = NO_BYTES;
        } else {
            _skip(len);
        }
    }

    @Override
    public JsonToken decodeFixed(int size) throws IOException {
        byte[] data = new byte[size];
        _read(data, 0, size);
        _binaryValue = data;
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    @Override
    public void skipFixed(int size) throws IOException {
        _skip(size);
    }

    private final void _read(byte[] target, int offset, int len) throws IOException
    {
        int ptr = _inputPtr;
        int available = _inputEnd - ptr;
        if (len <= available) { // already got it all?
            System.arraycopy(_inputBuffer, ptr, target, offset, len);
            _inputPtr = ptr + len;
            return;
        }
        // only had some, copy whatever there is
        System.arraycopy(_inputBuffer, ptr, target, offset, available);
        _inputPtr = ptr + available;
        offset += available;
        int left = len - available;
        // and rest we can read straight from input
        do {
            int count = _inputStream.read(target, offset, left);
            if (count <= 0) {
                _reportError("Needed to read "+len+" bytes, reached end-of-input after reading "+(len - left));
            }
            offset += count;
            left -= count;
        } while (left > 0);
    }

    private final void _skip(int len) throws IOException
    {
        int ptr = _inputPtr;
        int available = _inputEnd - ptr;
        int left = len - available;
        if (left <= 0) {
            _inputPtr = ptr + len;
            return;
        }
        _inputPtr = _inputEnd; // mark all used, whatever it was
        if (_inputStream != null) {
            do {
                int skipped = (int) _inputStream.skip(left);
                if (skipped < 0) {
                    break;
                }
                left -= skipped;
            } while (left > 0);
        }
        if (left > 0) {
            _reportError("Only able to skip "+(len-left)+" bytes before end-of-input (needed "+len+")");
        }
    }

    private final void _skipL(long len) throws IOException
    {
        int ptr = _inputPtr;
        int available = _inputEnd - ptr;
        long left = len - available;
        if (left <= 0L) {
            _inputPtr = ptr + (int)  len;
            return;
        }
        _inputPtr = _inputEnd; // mark all used, whatever it was
        if (_inputStream != null) {
            do {
                int skipped = (int) _inputStream.skip(left);
                if (skipped < 0) {
                    break;
                }
                left -= skipped;
            } while (left > 0L);
        }
        if (left > 0L) {
            _reportError("Only able to skip "+(len-left)+" bytes before end-of-input (needed "+len+")");
        }
    }
    
    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Arrays
    /**********************************************************
     */

    @Override
    public long decodeArrayStart() throws IOException {
        return _decodeChunkLength();
    }

    @Override
    public long decodeArrayNext() throws IOException {
        return _decodeChunkLength();
    }

    @Override
    public long skipArray() throws IOException {
        return _skipChunkElements();
    }

    // used for Arrays and Maps, first and other chunks
    private final long _decodeChunkLength() throws IOException {
        long result = decodeLong();
        if (result < 0) {
            skipLong(); // Consume byte-count if present
            result = -result;
        }
        return result;
    }

    private long _skipChunkElements() throws IOException {
        int result = decodeInt();
        while (result < 0) {
            long bytecount = decodeLong();
            _skipL(bytecount);
            result = decodeInt();
        }
        return result;
    }
    
    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Maps
    /**********************************************************
     */

    @Override
    public String decodeMapKey() throws IOException {
        decodeString();
        return _textBuffer.contentsAsString();
    }

    @Override
    public long decodeMapStart() throws IOException {
        return _decodeChunkLength();
    }

    @Override
    public long decodeMapNext() throws IOException {
        return _decodeChunkLength();
    }

    @Override
    public long skipMap() throws IOException {
        return _skipChunkElements();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: misc
    /**********************************************************
     */

    @Override
    public JsonToken decodeBoolean() throws IOException {
        int b = _nextByteGuaranteed();
        // As per Avro default impl: only `1` recognized as true (unlike
        // "C-style" 0 == false, others true)
        return (b == 1) ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
    }

    @Override
    public void skipBoolean() throws IOException {
        _skipByteGuaranteed();
    }

    @Override
    public int decodeIndex() throws IOException {
        return (_branchIndex = decodeInt());
    }

    @Override
    public int decodeEnum() throws IOException {
        return (_enumIndex = decodeInt());
    }

    @Override
    public boolean checkInputEnd() throws IOException {
        if (_closed) {
            return true;
        }
        if (_inputPtr < _inputEnd) {
            return false;
        }
        return !_loadMore();
    }

    /*
    /**********************************************************
    /* Low-level methods: setting values from defaults
    /**********************************************************
     */

    @Override
    protected JsonToken setString(String str) {
        _textBuffer.resetWithString(str);
        return JsonToken.VALUE_STRING;
    }

    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    private final byte _nextByteGuaranteed() throws IOException
    {
        int ptr = _inputPtr;
        if (ptr < _inputEnd) {
            byte b = _inputBuffer[ptr];
            _inputPtr = ptr+1;
            return b;
        }
        return _nextByteGuaranteed2();
    }

    private final byte _nextByteGuaranteed2() throws IOException
    {
        if (!_loadMore()) { _reportInvalidEOF(); }
        return _inputBuffer[_inputPtr++];
    }

    protected final void _loadMoreGuaranteed() throws IOException {
        if (!_loadMore()) { _reportInvalidEOF(); }
    }

    private final void _skipByteGuaranteed() throws IOException
    {
        int ptr = _inputPtr;
        if (ptr < _inputEnd) {
            _inputPtr = ptr+1;
            return;
        }
        _loadMoreGuaranteed();
        _inputPtr += 1;
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
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        _currInputProcessed += _inputPtr;
        if (_inputPtr > 0) {
            if (amount > 0) {
                //_currInputRowStart -= _inputPtr;
                System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
                _inputEnd = amount;
            } else {
                _inputEnd = 0;
            }
        }
        _inputPtr = 0;
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            _reportError("Needed to read %d bytes, reached end-of-input", minAvailable);
        }
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                // End of input
                _closeInput();
                // Should never return 0, so let's fail
                if (count == 0) {
                    throw new IOException("InputStream.read() returned 0 characters when trying to read "+amount+" bytes");
                }
                _reportError("Needed to read %d bytes, missed %d before end-of-input",
                        minAvailable, minAvailable);
            }
            _inputEnd += count;
        }
    }

    private void _reportInvalidNegative(int v) throws IOException
    {
        _reportError("Invalid negative byte %x at end of VInt", v);
    }
}
