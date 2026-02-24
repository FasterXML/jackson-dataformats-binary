package tools.jackson.dataformat.smile.parse;

import java.io.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing context (streamReadContext()) tracking at
 * root level and nested levels, for issue
 * https://github.com/FasterXML/jackson-dataformats-binary/issues/22
 */
public class ParserContextTest extends BaseTestForSmile
{
    @Test
    public void testRootContextObject() throws Exception
    {
        byte[] doc = _smileDoc("{\"a\": 1}");
        JsonParser p = _smileParser(doc);

        // Before first token
        assertNull(p.currentToken());
        assertTrue(p.streamReadContext().inRoot(),
                "Before first token, should be at root");
        assertNull(p.currentName());
        assertEquals("/", p.streamReadContext().toString());

        // START_OBJECT
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertTrue(p.streamReadContext().inObject(),
                "After START_OBJECT, should be inside object");
        assertNull(p.currentName(),
                "After root START_OBJECT, currentName should be null");
        assertEquals("{?}", p.streamReadContext().toString());

        // PROPERTY_NAME
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("a", p.currentName());
        assertEquals("{\"a\"}", p.streamReadContext().toString());

        // VALUE
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals("a", p.currentName());

        // END_OBJECT
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertTrue(p.streamReadContext().inRoot(),
                "After END_OBJECT, should be back at root");
        assertNull(p.currentName());
        assertEquals("/", p.streamReadContext().toString());

        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testRootContextArray() throws Exception
    {
        byte[] doc = _smileDoc("[1, 2, 3]");
        JsonParser p = _smileParser(doc);

        // Before first token
        assertNull(p.currentToken());
        assertTrue(p.streamReadContext().inRoot());

        // START_ARRAY
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertTrue(p.streamReadContext().inArray(),
                "After START_ARRAY, should be inside array");
        assertNull(p.currentName(),
                "After root START_ARRAY, currentName should be null");
        assertEquals("[0]", p.streamReadContext().toString());

        // VALUE 1 (index 0)
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals("[0]", p.streamReadContext().toString());

        // VALUE 2 (index 1)
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(2, p.getIntValue());
        assertEquals("[1]", p.streamReadContext().toString());

        // VALUE 3 (index 2)
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(3, p.getIntValue());
        assertEquals("[2]", p.streamReadContext().toString());

        // END_ARRAY
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertTrue(p.streamReadContext().inRoot(),
                "After END_ARRAY, should be back at root");
        assertNull(p.currentName());
        assertEquals("/", p.streamReadContext().toString());

        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testNestedContexts() throws Exception
    {
        byte[] doc = _smileDoc("{\"outer\": {\"inner\": 42}}");
        JsonParser p = _smileParser(doc);

        // START_OBJECT (outer)
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertEquals("{?}", p.streamReadContext().toString());

        // PROPERTY_NAME "outer"
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("outer", p.currentName());
        assertEquals("{\"outer\"}", p.streamReadContext().toString());

        // START_OBJECT (inner) - here, currentName should be "outer" (parent property)
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertEquals("outer", p.currentName(),
                "At START_OBJECT, currentName should reflect parent property name");
        assertEquals("{?}", p.streamReadContext().toString());

        // PROPERTY_NAME "inner"
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("inner", p.currentName());

        // VALUE 42
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(42, p.getIntValue());
        assertEquals("inner", p.currentName());

        // END_OBJECT (inner)
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertEquals("{\"outer\"}", p.streamReadContext().toString());

        // END_OBJECT (outer)
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertTrue(p.streamReadContext().inRoot());
        assertEquals("/", p.streamReadContext().toString());

        p.close();
    }

    @Test
    public void testArrayEntryCounting() throws Exception
    {
        byte[] doc = _smileDoc("[10, 20, 30]");
        JsonParser p = _smileParser(doc);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(0, p.streamReadContext().getEntryCount());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(10, p.getIntValue());
        assertEquals(1, p.streamReadContext().getEntryCount());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(20, p.getIntValue());
        assertEquals(2, p.streamReadContext().getEntryCount());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(30, p.getIntValue());
        assertEquals(3, p.streamReadContext().getEntryCount());

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }
}
