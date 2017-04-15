package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.AvroParser;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.CodecRecycler;

/**
 * Implementation class that exposes additional internal API
 * to be used as callbacks by {@link AvroReadContext} implementations.
 */
public class AvroParserImpl extends AvroParser
{
    protected final static byte[] NO_BYTES = new byte[0];

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
    /* Helper objects
    /**********************************************************
     */

    /**
     * Actual decoder in use, possible same as <code>_rootDecoder</code>, but
     * not necessarily, in case of different reader/writer schema in use.
     */
    protected BinaryDecoder _decoder;

    /*
    /**********************************************************
    /* Other decoding state
    /**********************************************************
     */

    /**
     * Index of the union branch that was followed to reach the current token. This is cleared when the next token is read.
     *
     * @since 2.9
     */
    protected int _branchIndex;

    /**
     * Index of the enum that was read as the current token. This is cleared when the next token is read.
     *
     * @since 2.9
     */
    protected int _enumIndex;

    /**
     * Value if decoded directly as `float`.
     *<p>
     * NOTE: base class (`ParserBase`) has other value storage, but since JSON
     * has no distinction between double, float, only includes `float`.
     *
     * @since 2.9
     */
    protected float _numberFloat;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public AvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec, InputStream in)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputPtr = 0;
        _inputEnd = 0;
        _bufferRecyclable = true;

        _decoder = CodecRecycler.decoder(in,
                Feature.AVRO_BUFFERING.enabledIn(avroFeatures));
    }

    public AvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec,
            byte[] data, int offset, int len)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = null;
        _decoder = CodecRecycler.decoder(data, offset, len);
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
        BinaryDecoder d = _decoder;
        if (d != null) {
            _decoder = null;
            CodecRecycler.release(d);
        }
    }

    /**
     * Skip to the end of the current structure (array/map/object); This is different from {@link #skipMap()} and {@link #skipArray()}
     * because it operates at the parser level instead of at the decoder level and advances the parsing context in addition to consuming
     * the data from the input.
     *
     * @throws IOException If there was an issue advancing through the underlying data stream
     */
    protected void skipValue() throws IOException {
        if (_avroContext instanceof ArrayReader) {
            ((ArrayReader) _avroContext).skipValue(this);
        } else if (_avroContext instanceof MapReader) {
            ((MapReader) _avroContext).skipValue(this);
        } else if (_avroContext instanceof RecordReader) {
            ((RecordReader) _avroContext).skipValue(this);
        }
    }

    @Override
    public JsonParser overrideFormatFeatures(int values, int mask) {
        int oldF = _formatFeatures;
        int newF = (_formatFeatures & ~mask) | (values & mask);

        if (oldF != newF) {
            _formatFeatures = newF;
            // 22-Oct-2015, tatu: Actually, not way to change buffering details at
            //   this point. If change needs to be dynamic have to change it
        }
        return this;
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

    /*
    /**********************************************************
    /* Abstract method impls, traversal
    /**********************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException
    {
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _branchIndex = -1;
        _enumIndex = -1;
        _binaryValue = null;
        if (_closed) {
            return null;
        }
        JsonToken t = _avroContext.nextToken();
        _currToken = t;
        return t;
    }

    @Override
    public String nextFieldName() throws IOException
    {
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;
        if (_closed) {
            return null;
        }
        String name = _avroContext.nextFieldName();
        if (name == null) {
            _currToken = _avroContext.getCurrentToken();
            return null;
        }
        _currToken = JsonToken.FIELD_NAME;
        return name;
    }

    @Override
    public boolean nextFieldName(SerializableString sstr) throws IOException
    {
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;
        if (_closed) {
            return false;
        }
        String name = _avroContext.nextFieldName();
        if (name == null) {
            _currToken = _avroContext.getCurrentToken();
            return false;
        }
        _currToken = JsonToken.FIELD_NAME;
        return name.equals(sstr.getValue());
    }

    @Override
    public String nextTextValue() throws IOException {
        return (nextToken() == JsonToken.VALUE_STRING) ? _textValue : null;
    }

    @Override
    protected void _initSchema(AvroSchema schema) throws JsonProcessingException {
        _avroContext = new RootReader(this, schema.getReader());
    }

    /*
    /**********************************************************
    /* Numeric accessors of public API
    /**********************************************************
     */

    @Override // since 2.9
    public boolean isNaN() {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                // 10-Mar-2017, tatu: Alas, `Double.isFinite(d)` only added in JDK 8
                double d = _numberDouble;
                return Double.isNaN(d) || Double.isInfinite(d);
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                float f = _numberFloat;
                return Float.isNaN(f) || Float.isInfinite(f);
            }
        }
        return false;
    }

    @Override
    public Number getNumberValue() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        // Separate types for int types
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return _numberInt;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return _numberLong;
            }
            if ((_numTypesValid & NR_BIGINT) != 0) {
                return _numberBigInt;
            }
            // Shouldn't get this far but if we do
            return _numberBigDecimal;
        }
    
        // And then floating point types. But here optimal type
        // needs to be big decimal, to avoid losing any data?
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return _numberBigDecimal;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return _numberDouble;
        }
        if ((_numTypesValid & NR_FLOAT) == 0) { // sanity check
            _throwInternal();
        }
        return _numberFloat;
    }

    @Override
    public NumberType getNumberType() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return NumberType.INT;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return NumberType.LONG;
            }
            return NumberType.BIG_INTEGER;
        }
    
        // And then floating point types. Here optimal type should be big decimal,
        // to avoid losing any data? However... using BD is slow, so let's allow returning
        // double as type if no explicit call has been made to access data as BD?
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return NumberType.BIG_DECIMAL;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return NumberType.DOUBLE;
        }
        return NumberType.FLOAT;
    }

    @Override
    public float getFloatValue() throws IOException
    {
        if ((_numTypesValid & NR_FLOAT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_FLOAT);
            }
            if ((_numTypesValid & NR_FLOAT) == 0) {
                convertNumberToFloat();
            }
        }
        // Bounds/range checks would be tricky here, so let's not bother even trying...
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value ("+getText()+") out of range of Java float");
        }
        */
        return _numberFloat;
    }

    /*
    /**********************************************************
    /* Numeric conversions
    /**********************************************************
     */

    protected void _checkNumericValue(int expType) throws IOException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token ("+getCurrentToken()+") not numeric, can not use numeric value accessors");
    }

    @Override
    protected void convertNumberToInt() throws IOException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportError("Numeric value ("+getText()+") out of range of int");
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0 
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }

    @Override
    protected void convertNumberToLong() throws IOException
    {
        if ((_numTypesValid & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0 
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                reportOverflowInt();
            }
            _numberLong = (long) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }

    @Override
    protected void convertNumberToBigInteger() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            _numberBigInt = _numberBigDecimal.toBigInteger();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberInt);
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberDouble).toBigInteger();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberFloat).toBigInteger();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGINT;
    }

    protected void convertNumberToFloat() throws IOException
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

    @Override
    protected void convertNumberToDouble() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberDouble = _numberBigDecimal.doubleValue();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberDouble = (double) _numberFloat;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberDouble = _numberBigInt.doubleValue();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberDouble = (double) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberDouble = (double) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_DOUBLE;
    }

    @Override
    protected void convertNumberToBigDecimal() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            // 05-Apt-2017, tatu: Unlike with textual formats, we never have textual
            //    representation to work with here
            _numberBigDecimal = new BigDecimal(_numberDouble);
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberFloat);
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberBigInt);
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberInt);
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGDECIMAL;
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding int
    /**********************************************************
     */

    public JsonToken decodeIntToken() throws IOException {
        _numberInt = decodeInt();
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }

    public int decodeInt() throws IOException {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 5) {
            return _decodeIntSlow();
        }
        final byte[] buf = _inputBuffer;
        int i = buf[ptr++];
        if (i < 0) {
            i &= 0x7F;
            int b = buf[ptr++];
            if (b < 0) {
                i += ((b & 0x7F) << 7);
                b = buf[ptr++];
                if (b < 0) {
                    i += ((b & 0x7F) << 14);
                    b = buf[ptr++];
                    if (b < 0) {
                        i += ((b & 0x7F) << 21);
                        b = buf[ptr++];
                        if (b < 0) {
                            _inputPtr = ptr;
                            _reportInvalidNegative(b);
                        }
                        i += (b << 28);
                    } else {
                        i += (b << 21);
                    }
                } else {
                    i += (b << 14);
                }
                
            } else {
                i += (b << 7);
            }
        }
        _inputPtr = ptr;
        // and final part: Zigzag decode
        return (i >>> 1) ^ (-(i & 1));
    }

    public int _decodeIntSlow() throws IOException {
        int i = _nextByteGuaranteed();
        if (i < 0) {
            i &= 0x7F;
            int b = _nextByteGuaranteed();
            if (b < 0) {
                i += ((b & 0x7F) << 7);
                b = _nextByteGuaranteed();
                if (b < 0) {
                    i += ((b & 0x7F) << 14);
                    b = _nextByteGuaranteed();
                    if (b < 0) {
                        i += ((b & 0x7F) << 21);
                        b = _nextByteGuaranteed();
                        if (b < 0) {
                            _reportInvalidNegative(b);
                        }
                        i += (b << 28);
                    } else {
                        i += (b << 21);
                    }
                } else {
                    i += (b << 14);
                }
                
            } else {
                i += (b << 7);
            }
        }
        // and final part: Zigzag decode
        return (i >>> 1) ^ (-(i & 1));
    }

    public void skipInt() throws IOException
    {
        int ptr = _inputPtr;
        if ((_inputEnd - ptr) < 5) {
            _skipIntSlow();
            return;
        }
        final byte[] buf = _inputBuffer;
        int b = buf[ptr++];
        if (b < 0) {
            b = buf[ptr++];
            if (b < 0) {
                b = buf[ptr++];
                if (b < 0) {
                    b = buf[ptr++];
                    if (b < 0) {
                        b = buf[ptr++];
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
        int b = _nextByteGuaranteed();
        if (b < 0) {
            b = _nextByteGuaranteed();
            if (b < 0) {
                b = _nextByteGuaranteed();
                if (b < 0) {
                    b = _nextByteGuaranteed();
                    if (b < 0) {
                        b = _nextByteGuaranteed();
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

    public JsonToken decodeLongToken() throws IOException {
        _numberLong = _decoder.readLong();
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    public void skipLong() throws IOException {
        // ints use variable-length zigzagging; alas, no native skipping
        _decoder.readLong();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding float/double
    /**********************************************************
     */
    
    public JsonToken decodeFloat() throws IOException {
        // !!! 10-Feb-2017, tatu: Should support float, see CBOR
        //   (requires addition of new NR_ constant, and possibly refactoring to
        //   use `ParserMinimalBase` instead of `ParserBase`)
        _numberDouble = _decoder.readFloat();
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    public void skipFloat() throws IOException {
        // floats have fixed length of 4 bytes
        _decoder.skipFixed(4);
    }

    public JsonToken decodeDouble() throws IOException {
        _numberDouble = _decoder.readDouble();
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    public void skipDouble() throws IOException {
        // doubles have fixed length of 8 bytes
        _decoder.skipFixed(8);
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Strings
    /**********************************************************
     */
    
    public JsonToken decodeString() throws IOException {
        _textValue = _decoder.readString();
        return JsonToken.VALUE_STRING;
    }

    public void skipString() throws IOException {
        _decoder.skipString();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Bytes
    /**********************************************************
     */
    
    public JsonToken decodeBytes() throws IOException {
        int len = _decoder.readInt();
        if (len <= 0) {
            _binaryValue = NO_BYTES;
        } else {
            byte[] b = new byte[len];
            // this is simple raw read, safe to use:
            _decoder.readFixed(b, 0, len);
            // plus let's retain reference to this buffer, for reuse
            // (is safe due to way Avro impl handles them)
            _binaryValue = b;
        }
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    public void skipBytes() throws IOException {
        _decoder.skipBytes();
    }

    public JsonToken decodeFixed(int size) throws IOException {
        byte[] data = new byte[size];
        _decoder.readFixed(data);
        _binaryValue = data;
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    public void skipFixed(int size) throws IOException {
        _decoder.skipFixed(size);
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Arrays
    /**********************************************************
     */

    public long decodeArrayStart() throws IOException {
        return _decoder.readArrayStart();
    }

    public long decodeArrayNext() throws IOException {
        return _decoder.arrayNext();
    }

    public long skipArray() throws IOException {
        return _decoder.skipArray();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding Maps
    /**********************************************************
     */

    public String decodeMapKey() throws IOException {
        return _decoder.readString();
    }

    public long decodeMapStart() throws IOException {
        return _decoder.readMapStart();
    }

    public long decodeMapNext() throws IOException {
        return _decoder.mapNext();
    }

    public long skipMap() throws IOException {
        return _decoder.skipMap();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: misc
    /**********************************************************
     */

    public JsonToken decodeBoolean() throws IOException {
        int b = _nextByteGuaranteed();
        // As per Avro default impl: only `1` recognized as true (unlike
        // "C-style" 0 == false, others true)
        return (b == 1) ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
    }

    public void skipBoolean() throws IOException {
        _skipByteGuaranteed();
    }

    public int decodeIndex() throws IOException {
        return (_branchIndex = decodeInt());
    }

    public int decodeEnum() throws IOException {
        return (_enumIndex = decodeInt());
    }

    public boolean checkInputEnd() throws IOException {
        if (_closed) {
            return true;
        }
        if (_inputPtr < _inputEnd) {
            return false;
        }
        return _loadMore();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext impls, other
    /**********************************************************
     */

    protected int branchIndex() {
        return _branchIndex;
    }

    protected int enumIndex() {
        return _enumIndex;
    }

    protected boolean isRecord() {
        return _avroContext instanceof RecordReader;
    }
    
    protected void setAvroContext(AvroReadContext ctxt) {
        if (ctxt == null) { // sanity check
            throw new IllegalArgumentException();
        }
        _avroContext = ctxt;
    }

    protected JsonToken setBytes(byte[] b)
    {
        _binaryValue = b;
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    protected JsonToken setNumber(int v) {
        _numberInt = v;
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }

    protected JsonToken setNumber(long v) {
        _numberLong = v;
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    protected JsonToken setNumber(float v) {
        _numberFloat = v;
        _numTypesValid = NR_FLOAT;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    protected JsonToken setNumber(double v) {
        _numberDouble = v;
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    protected JsonToken setString(String str) {
        _textValue = str;
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
        _loadMoreGuaranteed();
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

    private void _reportInvalidNegative(int v) throws IOException
    {
        _reportError("Invalid negative byte %x at end of VInt", v);
    }
}
