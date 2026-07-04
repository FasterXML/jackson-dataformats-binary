package com.fasterxml.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#598]
public class ParserStateEndTest extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    @Test
    public void testParserStateAtEndObject() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT);

        Point input = new Point(42, 13);
        byte[] bytes = MAPPER.writerFor(Point.class)
                .with(schema)
                .writeValueAsBytes(input);

        try (JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("x", p.currentName());

            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(42, p.getIntValue());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("y", p.currentName());

            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(13, p.getIntValue());

            assertToken(JsonToken.END_OBJECT, p.nextToken());

            assertFalse(p.isClosed(),
                "Parser should NOT be closed immediately after returning END_OBJECT");

            assertEquals(JsonToken.END_OBJECT, p.getCurrentToken(),
                "currentToken() should return END_OBJECT, not null");

            assertNull(p.nextToken(), "After END_OBJECT, nextToken() should return null");
            assertTrue(p.isClosed(), "Parser should be closed after nextToken() returns null");
        }
    }

    @Test
    public void testParserStateAtEndObjectWithNextFieldName() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT);

        Point input = new Point(42, 13);
        byte[] bytes = MAPPER.writerFor(Point.class)
                .with(schema)
                .writeValueAsBytes(input);

        try (JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertEquals("x", p.nextFieldName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

            assertEquals("y", p.nextFieldName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

            assertNull(p.nextFieldName());

            assertEquals(JsonToken.END_OBJECT, p.getCurrentToken(),
                "currentToken() should return END_OBJECT after nextFieldName() returns null");

            assertFalse(p.isClosed(),
                "Parser should NOT be closed when currentToken is END_OBJECT");

            assertNull(p.nextToken());
            assertTrue(p.isClosed());
        }
    }

    @Test
    public void testParserStateWithEmptyMessage() throws Exception
    {
        final String PROTOC_EMPTY = "message Empty {}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_EMPTY);

        // Empty Protobuf message is legitimately zero bytes: no fields to encode
        byte[] bytes = new byte[0];

        try (JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertFalse(p.isClosed());

            assertToken(JsonToken.END_OBJECT, p.nextToken());

            assertFalse(p.isClosed(),
                "Parser should NOT be closed immediately after END_OBJECT");
            assertEquals(JsonToken.END_OBJECT, p.getCurrentToken());

            assertNull(p.nextToken());
            assertTrue(p.isClosed());
        }
    }
}
