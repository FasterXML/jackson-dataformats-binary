package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncParserNamesTest extends AsyncTestBase
{
    @Test
    public void testLongNames() throws IOException
    {
        _testWithName(generateName(5000));
    }

    @Test
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

    @Test
    public void testSymbolTable() throws IOException
    {
        final String STR1 = "a";

        byte[] doc = _smileDoc("{ "+quote(STR1)+":1, \"foobar\":2, \"longername\":3 }");
        SmileFactory f = new SmileFactory();
        AsyncReaderWrapper p = asyncForBytes(f, 5, doc, 0);
        final ByteQuadsCanonicalizer symbols1 = ((NonBlockingByteArrayParser) p.parser()).symbolTableForTests();
        assertEquals(0, symbols1.size());

        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        // field names are interned:
        assertSame(STR1, p.currentName());
        assertEquals(1, symbols1.size());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertSame("foobar", p.currentName());
        assertEquals(2, symbols1.size());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertSame("longername", p.currentName());
        assertEquals(3, symbols1.size());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        assertEquals(3, symbols1.size());
        p.close();

        // but let's verify that symbol table gets reused properly
        p = asyncForBytes(f, 5, doc, 0);

        final ByteQuadsCanonicalizer symbols2 = ((NonBlockingByteArrayParser) p.parser()).symbolTableForTests();
        // symbol tables are not reused, but contents are:
        assertNotSame(symbols1, symbols2);
        assertEquals(3, symbols2.size());

        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        // field names are interned:
        assertSame(STR1, p.currentName());
        assertEquals(3, symbols2.size());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertSame("foobar", p.currentName());
        assertEquals(3, symbols2.size());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertSame("longername", p.currentName());
        assertEquals(3, symbols2.size());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        assertEquals(3, symbols2.size());
        p.close();

        assertEquals(3, symbols2.size());
        p.close();
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
