package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.cbor.*;
import tools.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// for [dataformat-binary#93]
public class UndefinedValueTest extends CBORTestBase
{
    private final static byte BYTE_UNDEFINED = (byte) CBORConstants.SIMPLE_VALUE_UNDEFINED;

    private final CBORFactory CBOR_F = CBORFactory.builder()
            .disable(CBORReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
            .build();

    @Test
    public void testUndefinedLiteralStreaming() throws Exception
    {
        try (CBORParser p = cborParser(CBOR_F, new byte[] { BYTE_UNDEFINED })) {
            assertEquals(JsonToken.VALUE_NULL, p.nextToken());
            assertTrue(p.isUndefined());
            assertNull(p.nextToken());
        }
    }

    // @since 2.20 [jackson-dataformats-binary/137]
    @Test
    public void testUndefinedLiteralAsEmbeddedObject() throws Exception {
        CBORParser p = cborParser(new byte[] { BYTE_UNDEFINED });

        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertTrue(p.isUndefined());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testUndefinedInArray() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(CBORConstants.BYTE_ARRAY_INDEFINITE);
        out.write(BYTE_UNDEFINED);
        out.write(CBORConstants.BYTE_BREAK);
        try (CBORParser p = cborParser(CBOR_F, out.toByteArray())) {
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            assertEquals(JsonToken.VALUE_NULL, p.nextToken());
            assertTrue(p.isUndefined());
            assertEquals(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    // @since 2.20 [jackson-dataformats-binary/137]
    @Test
    public void testUndefinedInArrayAsEmbeddedObject() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(CBORConstants.BYTE_ARRAY_INDEFINITE);
        out.write(BYTE_UNDEFINED);
        out.write(CBORConstants.BYTE_BREAK);
        CBORParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertTrue(p.isUndefined());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
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

        try (CBORParser p = cborParser(CBOR_F, doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("bar", p.currentName());
            assertEquals(JsonToken.VALUE_NULL, p.nextToken());
            assertTrue(p.isUndefined());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    // @since 2.20 [jackson-dataformats-binary/137]
    @Test
    public void testUndefinedInObjectAsEmbeddedObject() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator g = cborGenerator(out);
        g.writeStartObject();
        g.writeName("bar");
        g.writeBoolean(true);
        g.writeEndObject();
        g.close();

        byte[] doc = out.toByteArray();
        // assume we use end marker for Object, so
        doc[doc.length - 2] = BYTE_UNDEFINED;

        try (CBORParser p = cborParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("bar", p.currentName());
            assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertTrue(p.isUndefined());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }
}
