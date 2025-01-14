package com.fasterxml.jackson.dataformat.smile;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

import static org.junit.jupiter.api.Assertions.*;

public class SymbolHandlingTest extends BaseTestForSmile
{
    @Test
    public void testSymbolTable() throws IOException
    {
        final String STR1 = "a";

        byte[] data = _smileDoc("{ "+quote(STR1)+":1, \"foobar\":2, \"longername\":3 }");
        SmileFactory f = new SmileFactory();
        SmileParser p = _smileParser(f, data);
        final ByteQuadsCanonicalizer symbols1 = p._symbols;
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
        p = _smileParser(f, data);
        ByteQuadsCanonicalizer symbols2 = p._symbols;
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

}
