package tools.jackson.dataformat.smile.parse;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.*;

import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.*;

public class ParserReadContextTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Copied from databind `SimpleParserTest`
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

        final byte[] doc = _smileDoc(MAPPER.writer(), JSON);

        try (JsonParser p = MAPPER.createParser(doc)) {
            _testKeywords(p);
        }
    }

    private void _testKeywords(JsonParser p)
    {
        TokenStreamContext ctxt = p.streamReadContext();
        assertEquals("/", ctxt.toString());
        assertTrue(ctxt.inRoot());
        assertFalse(ctxt.inArray());
        assertFalse(ctxt.inObject());
        assertEquals(0, ctxt.getEntryCount());
        assertEquals(0, ctxt.getCurrentIndex());

        // Before advancing to content, we should have following default state...
        assertFalse(p.hasCurrentToken());
        assertNull(p.getString());
        assertNull(p.getStringCharacters());
        assertEquals(0, p.getStringLength());
        // not sure if this is defined but:
        assertEquals(0, p.getStringOffset());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertEquals("/", ctxt.toString());

        assertTrue(p.hasCurrentToken());
        TokenStreamLocation loc = p.currentTokenLocation();
        assertNotNull(loc);

        ctxt = p.streamReadContext();
        assertFalse(ctxt.inRoot());
        assertFalse(ctxt.inArray());
        assertTrue(ctxt.inObject());
        assertEquals(0, ctxt.getEntryCount());
        assertEquals(0, ctxt.getCurrentIndex());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        verifyFieldName(p, "key1");
        assertEquals("{\"key1\"}", ctxt.toString());

        ctxt = p.streamReadContext();
        assertFalse(ctxt.inRoot());
        assertFalse(ctxt.inArray());
        assertTrue(ctxt.inObject());
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(1, ctxt.getEntryCount());
        assertEquals("key1", ctxt.currentName());

        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals("key1", ctxt.currentName());

        ctxt = p.streamReadContext();
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(1, ctxt.getEntryCount());

        assertEquals("key2", p.nextName());
        verifyFieldName(p, "key2");
        ctxt = p.streamReadContext();
        assertEquals(1, ctxt.getCurrentIndex());
        assertEquals(2, ctxt.getEntryCount());
        assertEquals("key2", ctxt.currentName());

        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(1, ctxt.getCurrentIndex());
        assertEquals(2, ctxt.getEntryCount());
        assertEquals("key2", ctxt.currentName());

        assertTrue(p.nextName(new SerializedString("key3")));
        verifyFieldName(p, "key3");
        assertEquals(2, ctxt.getCurrentIndex());
        assertEquals(3, ctxt.getEntryCount());
        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals(2, ctxt.getCurrentIndex());
        assertEquals(3, ctxt.getEntryCount());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        verifyFieldName(p, "key4");
        assertEquals(3, ctxt.getCurrentIndex());
        assertEquals(4, ctxt.getEntryCount());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        ctxt = p.streamReadContext();
        assertTrue(ctxt.inArray());
        assertNull(ctxt.currentName());
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(0, ctxt.getEntryCount());
        assertEquals("key4", ctxt.getParent().currentName());

        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals(0, ctxt.getCurrentIndex());
        assertEquals(1, ctxt.getEntryCount());
        assertEquals("[0]", ctxt.toString());

        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(1, ctxt.getCurrentIndex());
        assertEquals(2, ctxt.getEntryCount());

        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(2, ctxt.getCurrentIndex());
        assertEquals(3, ctxt.getEntryCount());

        assertToken(JsonToken.END_ARRAY, p.nextToken());

        ctxt = p.streamReadContext();
        assertTrue(ctxt.inObject());

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        ctxt = p.streamReadContext();
        assertTrue(ctxt.inRoot());
        assertNull(ctxt.currentName());
    }

    private void verifyFieldName(JsonParser p, String expName)
    {
        assertEquals(expName, p.getString());
        assertEquals(expName, p.currentName());
    }

}
