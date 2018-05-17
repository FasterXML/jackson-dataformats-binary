package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.*;

public class WriteBinaryTest extends ProtobufTestBase
{
    final protected static String PROTOC_BINARY =
            "message Name {\n"
            +" optional int32 id = 1;\n"
            +" required bytes data = 3;\n"
            +" required int32 trailer = 2;\n"
            +"}\n"
    ;

    static class Binary {
        public int id, trailer;
        public byte[] data;

        public Binary() { }
        public Binary(int id, byte[] data, int trailer) {
            this.id = id;
            this.data = data;
            this.trailer = trailer;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = new ProtobufMapper();

    public void testSimpleBinary() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BINARY);
        final ObjectWriter w = MAPPER.writer(schema);
        byte[] data = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
        Binary input = new Binary(123, data, 456);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(18, bytes.length);

        Binary result = MAPPER.readerFor(Binary.class).with(schema)
                .readValue(bytes);
        assertEquals(input.id, result.id);
        assertEquals(input.trailer, result.trailer);
        assertNotNull(result.data);

        _verify(data, result.data);

        // and via JsonParser too
        JsonParser p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertFalse(p.hasTextCharacters());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(input.trailer, p.getIntValue());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("data", p.getCurrentName());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        _verify(data, p.getBinaryValue());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();

        // and with skipping of binary data
        p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(input.trailer, p.nextIntValue(-1));
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    private void _verify(byte[] dataExp, byte[] dataAct) {
        assertEquals(dataExp.length, dataAct.length);
        for (int i = 0, len = dataExp.length; i < len; ++i) {
            if (dataExp[i] != dataAct[i]) {
                fail("Binary data differs at #"+i);
            }
        }
    }
}
