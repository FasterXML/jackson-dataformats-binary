package tools.jackson.dataformat.smile.parse;

import java.io.*;

import tools.jackson.core.*;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileGenerator;

public class ParserLocationTest
    extends BaseTestForSmile
{
    public void testSimpleOffsets() throws IOException
    {
        byte[] data = _smileDoc("[ true, null, false, 511 ]", true); // true -> write header
        
        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());
        JsonLocation loc = p.currentLocation();
        assertNotNull(loc);
        // first: -1 for "not known", for character-based stuff
        assertEquals(-1, loc.getCharOffset());
        // column will indicate offset, so:
        assertEquals(4, loc.getColumnNr());
        assertEquals(-1, loc.getLineNr());
        // but first 4 bytes are for header
        assertEquals(4, loc.getByteOffset());

        // Let's verify Location info (quite minimal for Binary content)
        assertEquals("byte offset: #4", loc.offsetDescription());
        assertEquals("(byte[])[12 bytes]", loc.sourceDescription());

        // array marker is a single byte, so:
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(5, p.currentLocation().getByteOffset());
        assertEquals(4, p.currentTokenLocation().getByteOffset());

        // same for true and others except for last int
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(6, p.currentLocation().getByteOffset());
        assertEquals(5, p.currentTokenLocation().getByteOffset());

        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(7, p.currentLocation().getByteOffset());
        assertEquals(6, p.currentTokenLocation().getByteOffset());

        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals(8, p.currentLocation().getByteOffset());
        assertEquals(7, p.currentTokenLocation().getByteOffset());

        // 0x1FF takes 3 bytes (type byte, 7/6 bit segments)
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(511, p.getIntValue());
        assertEquals(11, p.currentLocation().getByteOffset());
        assertEquals(8, p.currentTokenLocation().getByteOffset());
        
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(12, p.currentLocation().getByteOffset());
        assertEquals(11, p.currentTokenLocation().getByteOffset());

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
        SmileGenerator gen = _smileGenerator(bytes, true);
        gen.disable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES);
        gen.writeStartArray();
        for (int i = 0; i < COUNT; ++i) {
            gen.writeString("abc123");
        }
        gen.writeEndArray();
        gen.close();
        byte[] b = bytes.toByteArray();
        assertEquals(4 + 2 + SIZE, b.length);

        JsonParser p = _smileParser(new ByteArrayInputStream(b));
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        // 4 byte header, start array read, so 4 bytes down:
        assertEquals(5, p.currentLocation().getByteOffset());
        for (int i = 0; i < COUNT; ++i) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(6 + i*7, p.currentLocation().getByteOffset());
            assertEquals("abc123", p.getText());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(SIZE+6, p.currentLocation().getByteOffset());
        assertNull(p.nextToken());
        assertEquals(SIZE+6, p.currentLocation().getByteOffset());
        p.close();
    }
}
