package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

public class SimpleArrayTest extends AsyncTestBase
{
    public void testBooleans() throws IOException
    {
        final SmileFactory f = new SmileFactory();
        f.enable(SmileParser.Feature.REQUIRE_HEADER);
        byte[] data = _smileDoc("[ true, false, true, true, false ]", true);
        // first, no offsets
        _testBooleans(f, data, 0, 100);
        _testBooleans(f, data, 0, 3);
        _testBooleans(f, data, 0, 1);

        // then with some
        _testBooleans(f, data, 1, 100);
        _testBooleans(f, data, 1, 3);
        _testBooleans(f, data, 1, 1);
    }

    private void _testBooleans(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }
}
