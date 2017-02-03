package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.ResolvingDecoder;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
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

    /**
     * Actual physical decoder from input, which must use the original writer
     * schema. 
     */
    public BinaryDecoder _rootDecoder;

    /**
     * Actual decoder in use, possible same as <code>_rootDecoder</code>, but
     * not necessarily, in case of different reader/writer schema in use.
     */
    protected ResolvingDecoder _decoder;

    protected ByteBuffer _byteBuffer;

    public AvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec, InputStream in)
    {
        super(ctxt, parserFeatures, avroFeatures, codec, in);
        _rootDecoder = CodecRecycler.decoder(in,
                Feature.AVRO_BUFFERING.enabledIn(avroFeatures));
    }

    public AvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec,
            byte[] data, int offset, int len)
    {
        super(ctxt, parserFeatures, avroFeatures, codec,
                data, offset, len);
        _rootDecoder = CodecRecycler.decoder(data, offset, len);
    }

    @Override
    protected void _releaseBuffers() throws IOException {
        super._releaseBuffers();
        BinaryDecoder d = _rootDecoder;
        if (d != null) {
            _rootDecoder = null;
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
    /* Abstract method impls
    /**********************************************************
     */
    @Override
	public boolean isEnd() throws IOException {
		return _rootDecoder.isEnd();
	}
    
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
        _decoder = schema.decoder(_rootDecoder);
        AvroStructureReader reader = schema.getReader(_decoder);
        RootReader root = new RootReader();
        _avroContext = reader.newReader(root, this, _decoder);
    }
    
    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations
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
