package tools.jackson.dataformat.smile.parse;

import java.io.*;
import java.util.Random;

import tools.jackson.core.*;
import tools.jackson.dataformat.smile.BaseTestForSmile;

public class TestParserNames extends BaseTestForSmile
{
    public void testLongNames() throws IOException
    {
        _testWithName(generateName(5000));
    }

    public void testJsonBinForLargeObjects() throws Exception
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
        byte[] data = _smileDoc("{"+quote(name)+":13}");
        // important: MUST use InputStream to enforce buffer boundaries!
        JsonParser p = _smileParser(new ByteArrayInputStream(data));
        assertNull(p.currentToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(name, p.currentName());

        // but also let's verify we can override the name if need be
//        String newName = "fake"+name;
//        p.overrideCurrentName(newName);
//        assertEquals(newName, p.currentName());
        
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(13, p.getIntValue());
        // and overridden name should stick, too
//        assertEquals(newName, p.currentName());
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
