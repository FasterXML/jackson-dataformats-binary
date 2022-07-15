package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.cbor.CBORConstants;
import tools.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.dataformat.cbor.CBORTestBase;

// for [dataformat-binary#93]
public class UndefinedValueTest extends CBORTestBase
{
    private final static byte BYTE_UNDEFINED = (byte) 0xF7;

    public void testUndefinedLiteralStreaming() throws Exception
    {
        JsonParser p = cborParser(new byte[] { BYTE_UNDEFINED });
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    public void testUndefinedInArray() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(CBORConstants.BYTE_ARRAY_INDEFINITE);
        out.write(BYTE_UNDEFINED);
        out.write(CBORConstants.BYTE_BREAK);
        JsonParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    public void testUndefinedInObject() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator g = cborGenerator(out);
        g.writeStartObject();
        g.writeName("bar");
        g.writeBoolean(true);
        g.writeEndObject();
        g.close();

        byte[] doc = out.toByteArray();
        // assume we use end marker for Object, so
        doc[doc.length-2] = BYTE_UNDEFINED;
        
        JsonParser p = cborParser(doc);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("bar", p.currentName());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }
}
