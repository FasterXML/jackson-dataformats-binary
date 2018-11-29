package com.fasterxml.jackson.dataformat.smile.filter;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.dataformat.smile.*;
import com.fasterxml.jackson.dataformat.smile.testutil.PrefixInputDecorator;
import com.fasterxml.jackson.dataformat.smile.testutil.PrefixOutputDecorator;

public class StreamingDecoratorsTest extends BaseTestForSmile
{
    public void testInputDecorators() throws Exception
    {
        final byte[] DOC = _smileDoc("[ 42 ]");
        final SmileFactory streamF = smileFactory(false,  true,  false);
        streamF.setInputDecorator(new PrefixInputDecorator(DOC));
        JsonParser p = streamF.createParser(new byte[0]);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(42, p.getIntValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    public void testOutputDecorators() throws Exception
    {
        final byte[] DOC = _smileDoc(" 137");
        // important! Do not add document header for this test
        final SmileFactory streamF = smileFactory(false,  false,  false);
        streamF.setOutputDecorator(new PrefixOutputDecorator(DOC));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        JsonGenerator g = streamF.createGenerator(bytes);
        g.writeString("foo");
        g.close();

        JsonParser p = streamF.createParser(bytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(137, p.getIntValue());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("foo", p.getText());
        p.close();
    }
}
