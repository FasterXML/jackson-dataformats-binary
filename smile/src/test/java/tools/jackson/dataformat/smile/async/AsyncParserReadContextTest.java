package tools.jackson.dataformat.smile.async;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncParserReadContextTest extends AsyncTestBase
{
    // Async version of ParserReadContextTest.keywords()
    // [dataformats-binary#674]
    @Test
    void keywords() throws Exception
    {
        final String JSON = "{\n"
            +"\"key1\" : null,\n"
            +"\"key2\" : true,\n"
            +"\"key3\" : false,\n"
            +"\"key4\" : [ false, null, true ]\n"
            +"}"
            ;

        byte[] data = _smileDoc(JSON, true);

        // Test with different read sizes to exercise async buffering
        _testKeywords(data, 0, 100);
        _testKeywords(data, 0, 3);
        _testKeywords(data, 0, 1);

        _testKeywords(data, 1, 100);
        _testKeywords(data, 1, 3);
        _testKeywords(data, 1, 1);
    }

    private void _testKeywords(byte[] data, int offset, int readSize)
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, offset);
        JsonParser p = r.parser();

        TokenStreamContext ctxt = p.streamReadContext();
        assertEquals("/", ctxt.toString());
        assertTrue(ctxt.inRoot());
        assertFalse(ctxt.inArray());
        assertFalse(ctxt.inObject());
        assertEquals(0, ctxt.getEntryCount());
        assertEquals(0, ctxt.getCurrentIndex());

        assertToken(JsonToken.START_OBJECT, r.nextToken());

        ctxt = p.streamReadContext();
        assertFalse(ctxt.inRoot());
        assertFalse(ctxt.inArray());
        assertTrue(ctxt.inObject());
        assertEquals(0, ctxt.getEntryCount());
        assertEquals(0, ctxt.getCurrentIndex());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("key1", p.currentName());
        assertEquals("{\"key1\"}", ctxt.toString());

        ctxt = p.streamReadContext();
        assertFalse(ctxt.inRoot());
        assertFalse(ctxt.inArray());
        assertTrue(ctxt.inObject());
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(1, ctxt.getEntryCount());
        assertEquals("key1", ctxt.currentName());

        assertToken(JsonToken.VALUE_NULL, r.nextToken());
        assertEquals("key1", ctxt.currentName());

        ctxt = p.streamReadContext();
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(1, ctxt.getEntryCount());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("key2", p.currentName());
        ctxt = p.streamReadContext();
        assertEquals(1, ctxt.getCurrentIndex());
        assertEquals(2, ctxt.getEntryCount());
        assertEquals("key2", ctxt.currentName());

        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertEquals(1, ctxt.getCurrentIndex());
        assertEquals(2, ctxt.getEntryCount());
        assertEquals("key2", ctxt.currentName());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("key3", p.currentName());
        assertEquals(2, ctxt.getCurrentIndex());
        assertEquals(3, ctxt.getEntryCount());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertEquals(2, ctxt.getCurrentIndex());
        assertEquals(3, ctxt.getEntryCount());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("key4", p.currentName());
        assertEquals(3, ctxt.getCurrentIndex());
        assertEquals(4, ctxt.getEntryCount());

        assertToken(JsonToken.START_ARRAY, r.nextToken());
        ctxt = p.streamReadContext();
        assertTrue(ctxt.inArray());
        assertNull(ctxt.currentName());
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(0, ctxt.getEntryCount());
        assertEquals("key4", ctxt.getParent().currentName());

        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(1, ctxt.getEntryCount());
        assertEquals("[0]", ctxt.toString());

        assertToken(JsonToken.VALUE_NULL, r.nextToken());
        assertEquals(1, ctxt.getCurrentIndex());
        assertEquals(2, ctxt.getEntryCount());

        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertEquals(2, ctxt.getCurrentIndex());
        assertEquals(3, ctxt.getEntryCount());

        assertToken(JsonToken.END_ARRAY, r.nextToken());

        ctxt = p.streamReadContext();
        assertTrue(ctxt.inObject());

        assertToken(JsonToken.END_OBJECT, r.nextToken());
        ctxt = p.streamReadContext();
        assertTrue(ctxt.inRoot());
        assertNull(ctxt.currentName());

        r.close();
    }
}
