package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

public class ParserLocationTest
    extends BaseTestForSmile
{
    public void testSimpleOffsets() throws IOException
    {
        byte[] data = _smileDoc("[ true, null, false, 511 ]", true); // true -> write header
        
        JsonParser p = _smileParser(data);
        assertNull(p.getCurrentToken());
        JsonLocation loc = p.getCurrentLocation();
        assertNotNull(loc);
        // first: -1 for "not known", for character-based stuff
        assertEquals(-1, loc.getCharOffset());
        // column will indicate offset, so:
        assertEquals(4, loc.getColumnNr());
        assertEquals(-1, loc.getLineNr());
        // but first 4 bytes are for header
        assertEquals(4, loc.getByteOffset());

        // array marker is a single byte, so:
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(5, p.getCurrentLocation().getByteOffset());
        assertEquals(4, p.getTokenLocation().getByteOffset());

        // same for true and others except for last int
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(6, p.getCurrentLocation().getByteOffset());
        assertEquals(5, p.getTokenLocation().getByteOffset());

        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(7, p.getCurrentLocation().getByteOffset());
        assertEquals(6, p.getTokenLocation().getByteOffset());

        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals(8, p.getCurrentLocation().getByteOffset());
        assertEquals(7, p.getTokenLocation().getByteOffset());

        // 0x1FF takes 3 bytes (type byte, 7/6 bit segments)
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(511, p.getIntValue());
        assertEquals(11, p.getCurrentLocation().getByteOffset());
        assertEquals(8, p.getTokenLocation().getByteOffset());
        
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(12, p.getCurrentLocation().getByteOffset());
        assertEquals(11, p.getTokenLocation().getByteOffset());

        assertNull(p.nextToken());
        p.close();
    }

    // for [databind-smile#24]
    public void testAscendingOffsets() throws Exception
    {
        // need to create big enough document, say at least 64k
        // but as importantly, try to create buffer boundaries by using 6-char (7-byte) ASCII strings
        final int COUNT = 57000;
        final int SIZE = COUNT * 7;
        
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(COUNT + 10);
        SmileGenerator gen = smileGenerator(bytes, true);
        gen.disable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES);
        gen.writeStartArray();
        for (int i = 0; i < COUNT; ++i) {
            gen.writeString("abc123");
        }
        gen.writeEndArray();
        gen.close();
        byte[] b = bytes.toByteArray();
        assertEquals(4 + 2 + SIZE, b.length);

        SmileParser p = _smileParser(new ByteArrayInputStream(b));
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        // 4 byte header, start array read, so 4 bytes down:
        assertEquals(5, p.getCurrentLocation().getByteOffset());
        for (int i = 0; i < COUNT; ++i) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(6 + i*7, p.getCurrentLocation().getByteOffset());
            assertEquals("abc123", p.getText());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(SIZE+6, p.getCurrentLocation().getByteOffset());
        assertNull(p.nextToken());
        assertEquals(SIZE+6, p.getCurrentLocation().getByteOffset());
        p.close();
    }
}
