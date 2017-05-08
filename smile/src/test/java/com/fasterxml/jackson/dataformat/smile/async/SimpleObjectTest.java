package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

public class SimpleObjectTest extends AsyncTestBase
{
    @JsonPropertyOrder(alphabetic=true)
    static class BooleanBean {
        public boolean a, b, c, d, e;
    }
    
    public void testBooleans() throws IOException
    {
        final SmileFactory f = new SmileFactory();
        f.enable(SmileParser.Feature.REQUIRE_HEADER);
        byte[] data = _smileDoc(aposToQuotes("{ 'a':true, 'b':false, 'c':true, 'd':true, 'e':false }"), true);
        // first, no offsets
        _testBooleans(f, data, 0, 100);

        // 07-May-2017, tatu: Won't pass until we handle gathering of bytes
        /*
        _testBooleans(f, data, 0, 3);
        _testBooleans(f, data, 0, 1);
*/

        // then with some
        _testBooleans(f, data, 1, 100);
/*        
        _testBooleans(f, data, 1, 3);
        _testBooleans(f, data, 1, 1);
*/
    }

    private void _testBooleans(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_OBJECT, r.nextToken());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("a", r.currentText());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("b", r.currentText());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("c", r.currentText());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("d", r.currentText());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("e", r.currentText());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());

        assertToken(JsonToken.END_OBJECT, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

}
