package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.avro.io.Decoder;
import org.apache.avro.util.Utf8;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.avro.deser.AvroParserImpl;

/**
 * Wraps an AvroParser instance and allows it to be advanced by reading from it like an avro {@link Decoder}
 */
public class DecoderOverAvroParser extends Decoder {

    protected final AvroParserImpl _parser;

    public DecoderOverAvroParser(AvroParserImpl parser) {
        _parser = parser;
    }

    /**
     * Reads in the next value token from the parser and validates that it's one of the expected tokens
     *
     * @param expectedTokens List of token types that is expected
     * @return The token that was consumed from the parser
     * @throws IOException              If there is an error while reading the next token
     * @throws IllegalArgumentException If the next token is not one of the <code>expectedTokens</code>
     */
    protected JsonToken consumeToken(JsonToken... expectedTokens) throws IOException {
        JsonToken token = nextValue();
        for (JsonToken expectedToken : expectedTokens) {
            if (token.equals(expectedToken)) {
                _parser.clearCurrentToken();
                return token;
            }
        }
        throw new IllegalArgumentException("Expected " + Arrays.toString(expectedTokens) + ", got: " + token);
    }

    /**
     * Advance to the next actual value token; This effectively discards the virtual JSON tokens inserted by the parser for
     * <code>START_OBJECT</code>, <code>END_OBJECT</code>, <code>START_ARRAY</code>, etc. that are not actually present in the underlying
     * data stream and stops after reading the first token that represents actual data
     *
     * @return The next data token
     * @throws IOException If there is an error while reading the next token
     */
    protected JsonToken nextValue() throws IOException {
        // Decoders don't care about start and end of records, or field names in records; swallow them
        while (((_parser.currentToken() == JsonToken.START_OBJECT || _parser.currentToken() == JsonToken.END_OBJECT
            || _parser.currentToken() == JsonToken.FIELD_NAME) && _parser.isRecord()) || _parser.currentToken() == null
            || _parser.currentToken() == JsonToken.END_ARRAY) {
            _parser.nextToken();
            if (_parser.currentToken() == null) {
                break;
            }
        }
        return _parser.currentToken();
    }

    /**
     * Advance to the next token that might contain union branching information; This effectively discards the virtual JSON tokens inserted
     * by the parser for <code>END_OBJECT</code>, <code>START_ARRAY</code>, etc. that are not actually present in the underlying data
     * stream and stops after reading the first token that represents actual data. <code>START_OBJECT</code> is not skipped because union
     * information is only available at the start of an object, and has been discarded by the parser by the time we reach the next actual
     * value.
     *
     * @return The next union branch token
     * @throws IOException If there is an error while reading the next token
     */
    protected JsonToken nextUnionValue() throws IOException {
        // Decoders don't care about end of records, or field names in records; swallow them
        while (((_parser.currentToken() == JsonToken.END_OBJECT || _parser.currentToken() == JsonToken.FIELD_NAME) && _parser.isRecord())
            || _parser.currentToken() == null || _parser.currentToken() == JsonToken.END_ARRAY) {
            _parser.nextToken();
            if (_parser.currentToken() == null) {
                break;
            }
        }
        return _parser.currentToken();
    }

    @Override
    public void readNull() throws IOException {
        consumeToken(JsonToken.VALUE_NULL);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return consumeToken(JsonToken.VALUE_FALSE, JsonToken.VALUE_TRUE) == JsonToken.VALUE_TRUE;
    }

    @Override
    public int readInt() throws IOException {
        consumeToken(JsonToken.VALUE_NUMBER_INT);
        return _parser.getIntValue();
    }

    @Override
    public long readLong() throws IOException {
        consumeToken(JsonToken.VALUE_NUMBER_INT);
        return _parser.getLongValue();
    }

    @Override
    public float readFloat() throws IOException {
        consumeToken(JsonToken.VALUE_NUMBER_FLOAT);
        return _parser.getFloatValue();
    }

    @Override
    public double readDouble() throws IOException {
        consumeToken(JsonToken.VALUE_NUMBER_FLOAT);
        return _parser.getDoubleValue();
    }

    @Override
    public Utf8 readString(Utf8 old) throws IOException {
        return new Utf8(readString());
    }

    @Override
    public String readString() throws IOException {
        nextValue();
        String value = _parser.getText();
        _parser.clearCurrentToken();
        return value;
    }

    @Override
    public void skipString() throws IOException {
        // Must read exactly one value: `readString()` also handles Map keys
        // (FIELD_NAME), unlike `consumeToken(JsonToken.VALUE_STRING)`
        readString();
    }

    @Override
    public ByteBuffer readBytes(ByteBuffer old) throws IOException {
        consumeToken(JsonToken.VALUE_EMBEDDED_OBJECT);
        byte[] value = _parser.getBinaryValue();
        if ((old != null) && value.length <= old.capacity()) {
            old.clear();
            old.put(value);
            old.flip();
            return old;
        }
        return ByteBuffer.wrap(value);
    }

    @Override
    public void skipBytes() throws IOException {
        readBytes(null);
    }

    @Override
    public void readFixed(byte[] bytes, int start, int length) throws IOException {
        consumeToken(JsonToken.VALUE_EMBEDDED_OBJECT);
        System.arraycopy(_parser.getBinaryValue(), 0, bytes, start, length);
    }

    @Override
    public void skipFixed(int length) throws IOException {
        readFixed(new byte[length], 0, length);
    }

    @Override
    public int readEnum() throws IOException {
        nextValue();
        return _parser.enumIndex();
    }

    @Override
    public long readArrayStart() throws IOException {
        consumeToken(JsonToken.START_ARRAY);
        return _parser.getRemainingElements();
    }

    @Override
    public long arrayNext() throws IOException {
        return _parser.getRemainingElements();
    }

    @Override
    public long skipArray() throws IOException {
        _skipStructure(JsonToken.START_ARRAY);
        return 0;
    }

    @Override
    public long readMapStart() throws IOException {
        consumeToken(JsonToken.START_OBJECT);
        return _parser.getRemainingElements();
    }

    @Override
    public long mapNext() throws IOException {
        return _parser.getRemainingElements();
    }

    @Override
    public long skipMap() throws IOException {
        _skipStructure(JsonToken.START_OBJECT);
        return 0;
    }

    /**
     * Helper method for skipping a full Array or Map value, including its end marker.
     *<p>
     * NOTE: can not use {@link AvroParserImpl#skipValue()}, as by the time start marker
     * has been returned, the reader has already consumed the first block count, and
     * {@code skipValue()} would try to read it again.
     * Instead the remaining contents are read (and discarded) token by token: this decodes
     * all keys and values, so it is no cheaper than reading the value.
     *
     * @since 2.21.8
     */
    private void _skipStructure(JsonToken startToken) throws IOException {
        JsonToken token = nextValue();
        if (token != startToken) {
            throw new IllegalArgumentException("Expected " + startToken + ", got: " + token);
        }
        // Read through the rest using currently open reader, up to and including matching
        // end marker; and then clear that so it is not seen as the next value
        _parser.skipChildren();
        _parser.clearCurrentToken();
    }

    @Override
    public int readIndex() throws IOException {
        nextUnionValue();
        return _parser.branchIndex();
    }
}
