package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.deser.AvroParserImpl;
import com.fasterxml.jackson.dataformat.avro.deser.AvroReadContext;

/**
 * Implementation class that exposes additional internal API
 * to be used as callbacks by {@link AvroReadContext} implementations.
 */
public class ApacheAvroParserImpl extends AvroParserImpl
{
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

    /**
     * We need to keep track of text values.
     */
    protected String _textValue;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public ApacheAvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec, InputStream in)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputPtr = 0;
        _inputEnd = 0;
        _bufferRecyclable = true;

        _decoder = ApacheCodecRecycler.decoder(in,
                Feature.AVRO_BUFFERING.enabledIn(avroFeatures));
    }

    public ApacheAvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec,
            byte[] data, int offset, int len)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = null;
        _decoder = ApacheCodecRecycler.decoder(data, offset, len);
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
            ApacheCodecRecycler.release(d);
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
            _inputStream.close();
        }
    }

    /*
    /**********************************************************
    /* Abstract method impls, text
    /**********************************************************
     */
    
    // For now we do not store char[] representation...
    @Override
    public boolean hasTextCharacters() {
        return false;
    }

    @Override
    public String nextTextValue() throws IOException {
        return (nextToken() == JsonToken.VALUE_STRING) ? _textValue : null;
    }

    @Override
    public String getText() throws IOException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textValue;
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            return _avroContext.getCurrentName();
        }
        if (_currToken != null) {
            if (_currToken.isScalarValue()) {
                return _textValue;
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
            writer.write(_textValue);
            return _textValue.length();
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
    /* Methods for AvroReadContext implementations: general state
    /**********************************************************
     */

    @Override
    public boolean checkInputEnd() throws IOException {
        return _decoder.isEnd();
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations: decoding
    /**********************************************************
     */
    
    @Override
    public JsonToken decodeBoolean() throws IOException {
        return _decoder.readBoolean() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
    }

    @Override
    public void skipBoolean() throws IOException {
        _decoder.skipFixed(1);
    }

    @Override
    public int decodeInt() throws IOException {
        return _decoder.readInt();
    }

    @Override
    public JsonToken decodeIntToken() throws IOException {
        _numberInt = _decoder.readInt();
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }
    
    @Override
    public void skipInt() throws IOException {
        // ints use variable-length zigzagging; alas, no native skipping
        _decoder.readInt();
    }

    @Override
    public long decodeLong() throws IOException {
        return _decoder.readLong();
    }

    @Override
    public JsonToken decodeLongToken() throws IOException {
        _numberLong = _decoder.readLong();
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    @Override
    public void skipLong() throws IOException {
        // ints use variable-length zigzagging; alas, no native skipping
        _decoder.readLong();
    }

    @Override
    public JsonToken decodeFloat() throws IOException {
        _numberFloat = _decoder.readFloat();
        _numTypesValid = NR_FLOAT;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    @Override
    public void skipFloat() throws IOException {
        // floats have fixed length of 4 bytes
        _decoder.skipFixed(4);
    }

    @Override
    public JsonToken decodeDouble() throws IOException {
        _numberDouble = _decoder.readDouble();
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    @Override
    public void skipDouble() throws IOException {
        // doubles have fixed length of 8 bytes
        _decoder.skipFixed(8);
    }

    @Override
    public void decodeString() throws IOException {
        _textValue = _decoder.readString();
    }

    @Override
    public JsonToken decodeStringToken() throws IOException {
        decodeString();
        return JsonToken.VALUE_STRING;
    }
    
    @Override
    public void skipString() throws IOException {
        _decoder.skipString();
    }

    @Override
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

    @Override
    public void skipBytes() throws IOException {
        _decoder.skipBytes();
    }

    @Override
    public JsonToken decodeFixed(int size) throws IOException {
        byte[] data = new byte[size];
        _decoder.readFixed(data);
        _binaryValue = data;
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    @Override
    public void skipFixed(int size) throws IOException {
        _decoder.skipFixed(size);
    }

    // // // Array decoding

    @Override
    public long decodeArrayStart() throws IOException {
        return _decoder.readArrayStart();
    }

    @Override
    public long decodeArrayNext() throws IOException {
        return _decoder.arrayNext();
    }

    @Override
    public long skipArray() throws IOException {
        return _decoder.skipArray();
    }

    // // // Map decoding
    
    @Override
    public String decodeMapKey() throws IOException {
        return _decoder.readString();
    }

    @Override
    public long decodeMapStart() throws IOException {
        return _decoder.readMapStart();
    }

    @Override
    public long decodeMapNext() throws IOException {
        return _decoder.mapNext();
    }

    @Override
    public long skipMap() throws IOException {
        return _decoder.skipMap();
    }
    
    // // // Misc other decoding
    
    @Override
    public int decodeIndex() throws IOException {
        return (_branchIndex = _decoder.readIndex());
    }

    @Override
    public int decodeEnum() throws IOException {
        return (_enumIndex = _decoder.readEnum());
    }

    /*
    /**********************************************************
    /* Methods for AvroReadContext impls, other
    /**********************************************************
     */

    @Override
    protected JsonToken setString(String str) {
        _textValue = str;
        return JsonToken.VALUE_STRING;
    }
}
