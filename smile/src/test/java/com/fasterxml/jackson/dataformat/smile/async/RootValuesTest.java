package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.*;

public class RootValuesTest extends AsyncTestBase
{
    private final SmileFactory F_REQ_HEADERS = new SmileFactory();
    {
        F_REQ_HEADERS.enable(SmileParser.Feature.REQUIRE_HEADER);
    }

    public void testSimpleRootSequence() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);

        // Let's simply concatenate documents...
        bytes.write(_smileDoc("[ true, false ]", true));
        bytes.write(_smileDoc("{ \"a\" : 4 }", true));
        bytes.write(_smileDoc(" 12356", true));
        bytes.write(_smileDoc(" true", true));
        byte[] input = bytes.toByteArray();

        SmileFactory f = F_REQ_HEADERS;
        _testSimpleRootSequence(f, input, 0, 900);
        _testSimpleRootSequence(f, input, 0, 3);
        _testSimpleRootSequence(f, input, 0, 1);

        _testSimpleRootSequence(f, input, 1, 900);
        _testSimpleRootSequence(f, input, 1, 3);
        _testSimpleRootSequence(f, input, 1, 1);
    }

    private void _testSimpleRootSequence(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
        assertNull(r.currentToken());

        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertToken(JsonToken.END_ARRAY, r.nextToken());
        assertNull(r.nextToken());
        // but note:
        assertFalse(r.isClosed());

        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("a", r.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(4, r.getIntValue());
        assertToken(JsonToken.END_OBJECT, r.nextToken());
        assertNull(r.nextToken());
        assertFalse(r.isClosed());
        
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(12356, r.getIntValue());
        assertNull(r.nextToken());
        assertFalse(r.isClosed());

        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        // but this is the real end:
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }
}
