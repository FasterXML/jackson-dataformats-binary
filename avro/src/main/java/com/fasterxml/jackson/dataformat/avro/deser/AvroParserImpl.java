package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

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
    /* Input source config, state
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

    /**
     * Lazily created {@link ByteBuffer} that is needed for decoding stuff when using
     * Avro stdlib.
     */
    protected ByteBuffer _byteBuffer;

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
            _inputStream.close();
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
        AvroStructureReader reader = schema.getReader();
        RootReader root = new RootReader();
        _avroContext = reader.newReader(root, this);
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding
    /**********************************************************
     */

    public JsonToken decodeBoolean() throws IOException {
        return _decoder.readBoolean() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
    }

    public void skipBoolean() throws IOException {
        _decoder.skipFixed(1);
    }

    public JsonToken decodeInt() throws IOException {
        _numberInt = _decoder.readInt();
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }

    public void skipInt() throws IOException {
        // ints use variable-length zigzagging; alas, no native skipping
        _decoder.readInt();
    }

    public JsonToken decodeLong() throws IOException {
        _numberLong = _decoder.readLong();
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    public void skipLong() throws IOException {
        // ints use variable-length zigzagging; alas, no native skipping
        _decoder.readLong();
    }

    public JsonToken decodeFloat() throws IOException {
        // !!! 10-Feb-2017, tatu: Should support float, see CBOR
        _numberDouble = _decoder.readDouble();
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

    public JsonToken decodeString() throws IOException {
        _textValue = _decoder.readString();
        return JsonToken.VALUE_STRING;
    }

    public void skipString() throws IOException {
        _decoder.skipString();
    }

    public JsonToken decodeBytes() throws IOException {
        ByteBuffer bb = borrowByteBuffer();
        bb = _decoder.readBytes(bb);
        // inlined `setBytes(bb)`:
        int len = bb.remaining();
        if (len <= 0) {
            _binaryValue = NO_BYTES;
        } else {
            _binaryValue = new byte[len];
            bb.get(_binaryValue);
            // plus let's retain reference to this buffer, for reuse
            // (is safe due to way Avro impl handles them)
            _byteBuffer = bb;
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

    // // // Array decoding

    public long decodeArrayStart() throws IOException {
        return _decoder.readArrayStart();
    }

    public long decodeArrayNext() throws IOException {
        return _decoder.arrayNext();
    }

    public long skipArray() throws IOException {
        return _decoder.skipArray();
    }

    // // // Map decoding
    
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
    
    // // // Misc other decoding
    
    public int decodeIndex() throws IOException {
        return _decoder.readIndex();
    }

    public int decodeEnum() throws IOException {
        return _decoder.readEnum();
    }

    public boolean checkInputEnd() throws IOException {
        return _decoder.isEnd();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext impls, other
    /**********************************************************
     */
    
    protected void setAvroContext(AvroReadContext ctxt) {
        if (ctxt == null) { // sanity check
            throw new IllegalArgumentException();
        }
        _avroContext = ctxt;
    }

    protected ByteBuffer borrowByteBuffer() {
        return _byteBuffer;
    }
    
    protected JsonToken setBytes(ByteBuffer bb)
    {
        int len = bb.remaining();
        if (len <= 0) {
            _binaryValue = NO_BYTES;
        } else {
            _binaryValue = new byte[len];
            bb.get(_binaryValue);
            // plus let's retain reference to this buffer, for reuse
            // (is safe due to way Avro impl handles them)
            _byteBuffer = bb;
        }
        return JsonToken.VALUE_EMBEDDED_OBJECT;
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
        _numberDouble = v;
        _numTypesValid = NR_DOUBLE;
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
}
