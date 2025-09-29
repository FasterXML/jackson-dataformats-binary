package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// for [dataformat-binary#93]
public class UndefinedValueTest extends CBORTestBase
{
    private final static byte BYTE_UNDEFINED = (byte) CBORConstants.SIMPLE_VALUE_UNDEFINED;

    private final CBORFactory CBOR_F = cborFactory();

    @Test
    public void testUndefinedLiteralStreaming() throws Exception
    {
        CBORParser p = cborParser(CBOR_F, new byte[] { BYTE_UNDEFINED });
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertTrue(p.isUndefined());
        assertNull(p.nextToken());
        p.close();
    }

    // @since 2.20 [jackson-dataformats-binary/137]
    @Test
    public void testUndefinedLiteralAsEmbeddedObject() throws Exception {
        CBORFactory f = CBORFactory.builder()
                .enable(CBORParser.Feature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
                .build();
        CBORParser p = cborParser(f, new byte[] { BYTE_UNDEFINED });

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
        CBORParser p = cborParser(CBOR_F, out.toByteArray());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertTrue(p.isUndefined());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    // @since 2.20 [jackson-dataformats-binary/137]
    @Test
    public void testUndefinedInArrayAsEmbeddedObject() throws Exception {
        CBORFactory f = CBORFactory.builder()
                .enable(CBORParser.Feature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(CBORConstants.BYTE_ARRAY_INDEFINITE);
        out.write(BYTE_UNDEFINED);
        out.write(CBORConstants.BYTE_BREAK);
        CBORParser p = cborParser(f, out.toByteArray());
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
        g.writeFieldName("bar");
        g.writeBoolean(true);
        g.writeEndObject();
        g.close();

        byte[] doc = out.toByteArray();
        // assume we use end marker for Object, so
        doc[doc.length-2] = BYTE_UNDEFINED;

        CBORParser p = cborParser(CBOR_F, doc);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("bar", p.currentName());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertTrue(p.isUndefined());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    // @since 2.20 [jackson-dataformats-binary/137]
    @Test
    public void testUndefinedInObjectAsEmbeddedObject() throws Exception {
        CBORFactory f = CBORFactory.builder()
                .enable(CBORParser.Feature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator g = cborGenerator(out);
        g.writeStartObject();
        g.writeFieldName("bar");
        g.writeBoolean(true);
        g.writeEndObject();
        g.close();

        byte[] doc = out.toByteArray();
        // assume we use end marker for Object, so
        doc[doc.length - 2] = BYTE_UNDEFINED;

        CBORParser p = cborParser(f, doc);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("bar", p.currentName());
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertTrue(p.isUndefined());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }
}
