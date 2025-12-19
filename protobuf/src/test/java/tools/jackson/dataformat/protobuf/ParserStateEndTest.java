package tools.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for issue #598: Protobuf parser state handling wrong for implicit close (END_OBJECT)
 */
public class ParserStateEndTest extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    /**
     * Test that verifies the parser properly handles the end-of-input state.
     * The parser should NOT be closed when returning the final END_OBJECT token;
     * it should only be closed on the subsequent nextToken() call.
     */
    @Test
    public void testParserStateAtEndObject() throws Exception
    {
        // Use a simple Point schema
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT);

        // Create test data
        Point input = new Point(42, 13);
        byte[] bytes = MAPPER.writerFor(Point.class)
                .with(schema)
                .writeValueAsBytes(input);

        // Parse with streaming parser
        try (JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
    
            // First field: "x"
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("x", p.currentName());
    
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(42, p.getIntValue());
    
            // Second field: "y"
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("y", p.currentName());
    
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(13, p.getIntValue());
    
            // END_OBJECT - This is the critical test
            assertToken(JsonToken.END_OBJECT, p.nextToken());
    
            // THIS IS THE KEY ASSERTION: Parser should NOT be closed yet
            // The parser should only be closed on the NEXT nextToken() call
            assertFalse(p.isClosed(),
                "Parser should NOT be closed immediately after returning END_OBJECT");
    
            // Verify currentToken() returns END_OBJECT (not null)
            assertEquals(JsonToken.END_OBJECT, p.currentToken(),
                "currentToken() should return END_OBJECT, not null");
    
            // Now the next token should be null AND close the parser
            assertNull(p.nextToken(), "After END_OBJECT, nextToken() should return null");
            assertTrue(p.isClosed(), "Parser should be closed after nextToken() returns null");
        }
    }

    /**
     * Similar test but using nextName() optimization
     */
    @Test
    public void testParserStateAtEndObjectWithNextName() throws Exception
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
    
            // Use nextName() for field access
            assertEquals("x", p.nextName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
    
            assertEquals("y", p.nextName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
    
            // Should get null from nextName() at end
            assertNull(p.nextName());
    
            // Current token should be END_OBJECT
            assertEquals(JsonToken.END_OBJECT, p.currentToken(),
                "currentToken() should return END_OBJECT after nextName() returns null");
    
            // Parser should NOT be closed yet
            assertFalse(p.isClosed(),
                "Parser should NOT be closed when currentToken is END_OBJECT");
    
            // Next token should be null and close parser
            assertNull(p.nextToken());
            assertTrue(p.isClosed());
        }
    }

    /**
     * Test with empty message (no fields)
     */
    @Test
    public void testParserStateWithEmptyMessage() throws Exception
    {
        final String PROTOC_EMPTY = "message Empty {}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_EMPTY);

        // Empty message = just START_OBJECT, END_OBJECT
        byte[] bytes = MAPPER.writer()
                .with(schema)
                .writeValueAsBytes(new Object());

        try (JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes)) {
            // START_OBJECT
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertFalse(p.isClosed());
    
            // END_OBJECT immediately
            assertToken(JsonToken.END_OBJECT, p.nextToken());
    
            // Parser should NOT be closed yet
            assertFalse(p.isClosed(),
                "Parser should NOT be closed immediately after END_OBJECT");
            assertEquals(JsonToken.END_OBJECT, p.currentToken());
    
            // Next token closes
            assertNull(p.nextToken());
            assertTrue(p.isClosed());
        }
    }
}
