package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.*;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.StreamReadConstraints;
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
    /* Configuration
    /**********************************************************
     */

    /**
     * @since 2.16
     */
    protected final static DecoderFactory DECODER_FACTORY = DecoderFactory.get();

    /**
     * @since 2.16
     */
    protected ApacheCodecRecycler _apacheCodecRecycler;

    /*
    /**********************************************************
    /* Input source config
    /**********************************************************
     */

    protected InputStream _inputStream;

    /**
     * Non-null only for non-buffering {@link InputStream} input, where Apache's
     * direct decoder cannot answer {@code isEnd()}.
     */
    protected final PushbackInputStream _pushbackInput;

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
     * Wrapper counting bytes pulled from the source, if a document length limit is
     * configured; {@code null} if not, or for fixed-buffer input.
     *
     * @since 2.18.11
     */
    protected final LengthCheckingInputStream _lengthCheckingInput;

    /**
     * Whether decoder buffers (and hence reads ahead of the decoding position).
     *
     * @since 2.18.11
     */
    protected final boolean _bufferingDecoder;

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
            ApacheCodecRecycler apacheCodecRecycler,
            ObjectCodec codec, InputStream in)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputPtr = 0;
        _inputEnd = 0;
        _bufferRecyclable = true;

        _apacheCodecRecycler = apacheCodecRecycler;
        final boolean buffering = Feature.AVRO_BUFFERING.enabledIn(avroFeatures);
        // [dataformats-binary#785] Apache decoder does its own buffering, so document
        // length constraint has to be applied by counting bytes it pulls from the stream.
        // Mid-value that count runs ahead of the decoding position by up to the buffer
        // size, so the in-stream check allows that much slack, to avoid failing a caller
        // for content it has not decoded; [dataformats-binary#806] then checks the exact
        // decoding position at every token boundary, where it can be determined
        if (_streamReadConstraints.hasMaxDocumentLength()) {
            LengthCheckingInputStream lengthChecking = new LengthCheckingInputStream(in,
                    _streamReadConstraints,
                    buffering ? DECODER_FACTORY.getConfiguredBufferSize() : 0);
            _lengthCheckingInput = lengthChecking;
            in = lengthChecking;
        } else {
            _lengthCheckingInput = null;
        }
        _bufferingDecoder = buffering;
        if (buffering) {
            _pushbackInput = null;
        } else {
            _pushbackInput = new PushbackInputStream(in, 1);
        }
        BinaryDecoder decoderToReuse = apacheCodecRecycler.acquireDecoder();
        _decoder = buffering
                ? DECODER_FACTORY.binaryDecoder(in, decoderToReuse)
                : DECODER_FACTORY.directBinaryDecoder(_pushbackInput, decoderToReuse);
    }

    public ApacheAvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ApacheCodecRecycler apacheCodecRecycler,
            ObjectCodec codec,
            byte[] buffer, int offset, int len)
    {
        super(ctxt, parserFeatures, avroFeatures, codec);
        _inputStream = null;
        _pushbackInput = null;
        _apacheCodecRecycler = apacheCodecRecycler;
        // fixed buffer: length validated up front by factory, nothing to count
        _lengthCheckingInput = null;
        _bufferingDecoder = true;
        BinaryDecoder decoderToReuse = apacheCodecRecycler.acquireDecoder();
        _decoder = DECODER_FACTORY.binaryDecoder(buffer, offset, len, decoderToReuse);
    }

    /**
     * [dataformats-binary#806]: at a token boundary the decoder is in a stable state, so
     * the exact decoding position can be determined -- bytes pulled from the source, less
     * what the decoder still holds buffered -- and the document length limit applied to
     * that rather than to the read-ahead-inflated raw count.
     *
     * @since 2.18.11
     */
    @Override
    public JsonToken nextToken() throws IOException
    {
        JsonToken t = super.nextToken();
        final LengthCheckingInputStream input = _lengthCheckingInput;
        if (input != null) {
            long consumed = input.bytesRead();
            if (_bufferingDecoder) {
                // NOTE: for a buffering decoder this is what remains in its buffer,
                //   undecoded; a direct decoder does not buffer, so raw count is exact
                consumed -= _decoder.inputStream().available();
            }
            if (consumed > 0L) {
                _streamReadConstraints.validateDocumentLength(consumed);
            }
        }
        return t;
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
        ApacheCodecRecycler recycler = _apacheCodecRecycler;
        if (recycler != null) {
            _apacheCodecRecycler = null;
            BinaryDecoder d = _decoder;
            if (d != null) {
                _decoder = null;
                recycler.release(d);
            }
            recycler.releaseToPool();
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
        if (_pushbackInput != null) {
            int b = _pushbackInput.read();
            if (b < 0) {
                return true;
            }
            _pushbackInput.unread(b);
            return false;
        }
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

    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    /**
     * {@link InputStream} wrapper that applies {@link StreamReadConstraints#validateDocumentLength}
     * to the number of bytes read so far, for use with Apache {@link BinaryDecoder} which
     * reads from the stream directly.
     *<p>
     * Note that a buffering {@link BinaryDecoder} pulls content from the stream ahead of
     * the actual decoding position, by up to its buffer size: bytes that have been read
     * but not (yet) decoded must not count towards document length, or a caller could be
     * failed for content it never decoded. So this check allows the buffer size as slack,
     * which makes it a backstop -- it bounds how much can be pulled while decoding a single
     * value -- rather than the primary check. The exact decoding position is applied at
     * every token boundary instead, by {@link ApacheAvroParserImpl#nextToken()}
     * [dataformats-binary#806].
     *
     * @since 2.18.11
     */
    private final static class LengthCheckingInputStream extends FilterInputStream
    {
        private final StreamReadConstraints _constraints;

        /**
         * Maximum number of bytes decoder may have read but not yet decoded.
         */
        private final int _readAheadSlack;

        private long _bytesRead;

        LengthCheckingInputStream(InputStream in, StreamReadConstraints constraints,
                int readAheadSlack) {
            super(in);
            _constraints = constraints;
            _readAheadSlack = readAheadSlack;
        }

        public long bytesRead() { return _bytesRead; }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                ++_bytesRead;
                _validateLength();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = in.read(b, off, len);
            if (count > 0) {
                _bytesRead += count;
                _validateLength();
            }
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            long count = in.skip(n);
            if (count > 0) {
                _bytesRead += count;
                _validateLength();
            }
            return count;
        }

        private void _validateLength() throws IOException {
            // [dataformats-binary#806]: trip point is unchanged -- read-ahead is still
            //   allowed as slack -- but what gets reported is the real count of bytes
            //   pulled, not that count less the slack, which is not a document length
            final long maxLen = _constraints.getMaxDocumentLength();
            if ((maxLen > 0L) && ((_bytesRead - _readAheadSlack) > maxLen)) {
                _constraints.validateDocumentLength(_bytesRead);
            }
        }
    }
}
