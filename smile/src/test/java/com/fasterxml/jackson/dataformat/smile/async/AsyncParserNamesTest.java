package com.fasterxml.jackson.dataformat.smile.async;

import java.io.*;
import java.util.Random;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class AsyncParserNamesTest extends AsyncTestBase
{
    public void testLongNames() throws IOException
    {
        _testWithName(generateName(5000));
    }
    
    public void testEvenLongerName() throws Exception
    {
        StringBuilder nameBuf = new StringBuilder("longString");
        int minLength = 9000;
        for (int i = 1; nameBuf.length() < minLength; ++i) {
            nameBuf.append("." + i);
        }
        String name = nameBuf.toString();
        _testWithName(name);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void _testWithName(String name) throws IOException
    {
        SmileFactory f = new SmileFactory();
        byte[] doc = _smileDoc("{"+quote(name)+":13}");
        // important: MUST use InputStream to enforce buffer boundaries!
        AsyncReaderWrapper p = asyncForBytes(f, 37, doc, 0);

        assertNull(p.currentToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(name, p.currentName());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(13, p.getIntValue());
        assertEquals(name, p.currentName());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    private String generateName(int minLen)
    {
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random(123);
        while (sb.length() < minLen) {
    		int ch = rnd.nextInt(96);
    		if (ch < 32) { // ascii (single byte)
    			sb.append((char) (48 + ch));
    		} else if (ch < 64) { // 2 byte
    			sb.append((char) (128 + ch));
    		} else { // 3 byte
    			sb.append((char) (4000 + ch));
    		}
        }
        return sb.toString();
    }
}
