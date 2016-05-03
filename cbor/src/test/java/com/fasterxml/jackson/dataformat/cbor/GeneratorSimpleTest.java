package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeneratorSimpleTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();
    
    /**
     * Test for verifying handling of 'true', 'false' and 'null' literals
     */
    public void testSimpleLiterals() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);

        assertEquals(0, gen.getOutputBuffered());
        gen.writeBoolean(true);
        assertEquals(1, gen.getOutputBuffered());
        
        gen.close();
        assertEquals(0, gen.getOutputBuffered());
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_TRUE);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeBoolean(false);
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_FALSE);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNull();
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_NULL);
    }

    public void testMinimalIntValues() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        assertTrue(gen.isEnabled(CBORGenerator.Feature.WRITE_MINIMAL_INTS));
        gen.writeNumber(17);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 17));

        // then without minimal
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS);
        gen.writeNumber(17);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 26),
                (byte) 0, (byte) 0, (byte) 0, (byte) 17);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(Integer.MAX_VALUE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 26),
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(Integer.MIN_VALUE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 26),
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    }

    public void testIntValues() throws Exception
    {
        // first, single-byte
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeNumber(13);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 13));

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(-13);
        gen.close();
        _verifyBytes(out.toByteArray(),
                // note: since there is no "-0", number one less than it'd appear
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 12));

        // then two byte
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(0xFF);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 24), (byte) 0xFF);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(-256);
        gen.close();
        _verifyBytes(out.toByteArray(),
                // note: since there is no "-0", number one less than it'd appear
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 24), (byte) 0xFF);

        // and three byte
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(0xFEDC);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 25), (byte) 0xFE, (byte) 0xDC);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(-0xFFFE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 25), (byte) 0xFF, (byte) 0xFD);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(Integer.MAX_VALUE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 26),
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber(Integer.MIN_VALUE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 26),
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    }

    public void testLongValues() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        long l = -1L + Integer.MIN_VALUE;
        gen.writeNumber(l);
        gen.close();
        byte[] b = out.toByteArray();
        assertEquals((byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 27), b[0]);
        assertEquals(9, b.length);
        // could test full contents, but for now this shall suffice
        assertEquals(0, b[1]);
        assertEquals(0, b[2]);
        assertEquals(0, b[3]);
    }
    
    public void testFloatValues() throws Exception
    {
        // first, 32-bit float
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        float f = 1.25f;
        gen.writeNumber(f);
        gen.close();
        int raw = Float.floatToIntBits(f);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.BYTE_FLOAT32),
                (byte) (raw >> 24),
                (byte) (raw >> 16),
                (byte) (raw >> 8),
                (byte) raw);

        // then 64-bit double
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        double d = 0.75f;
        gen.writeNumber(d);
        gen.close();
        long rawL = Double.doubleToLongBits(d);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.BYTE_FLOAT64),
                (byte) (rawL >> 56),
                (byte) (rawL >> 48),
                (byte) (rawL >> 40),
                (byte) (rawL >> 32),
                (byte) (rawL >> 24),
                (byte) (rawL >> 16),
                (byte) (rawL >> 8),
                (byte) rawL);
    }

    public void testEmptyArray() throws Exception
    {
        // First: empty array (2 bytes)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartArray();
        gen.writeEndArray();
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_ARRAY_INDEFINITE,
               CBORConstants.BYTE_BREAK);
    }

    public void testEmptyObject() throws Exception
    {
        // First: empty array (2 bytes)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartObject();
        gen.writeEndObject();
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_OBJECT_INDEFINITE,
               CBORConstants.BYTE_BREAK);
    }
    
    public void testIntArray() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        
        // currently will produce indefinite-length array
        gen.writeStartArray();
        gen.writeNumber(1);
        gen.writeNumber(2);
        gen.writeNumber(3);
        gen.writeEndArray();
        gen.close();
        
        final byte[] EXP = new byte[] {
                CBORConstants.BYTE_ARRAY_INDEFINITE,
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 1),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 2),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 3),
                CBORConstants.BYTE_BREAK
        };
        
        _verifyBytes(out.toByteArray(), EXP);

        // Also, data-binding should produce identical
        byte[] b = MAPPER.writeValueAsBytes(new int[] { 1, 2, 3 });
        _verifyBytes(b, EXP);
    }

    public void testTrivialObject() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        
        // currently will produce indefinite-length Object
        gen.writeStartObject();
        gen.writeNumberField("a", 1);
        gen.writeNumberField("b", 2);
        gen.writeEndObject();
        gen.close();

        final byte[] EXP = new byte[] {
                CBORConstants.BYTE_OBJECT_INDEFINITE,
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 1),
                (byte) 'a',
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 1),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 1),
                (byte) 'b',
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 2),
                CBORConstants.BYTE_BREAK
        };

        _verifyBytes(out.toByteArray(), EXP);
        Map<String,Integer> map = new LinkedHashMap<String,Integer>();
        map.put("a", 1);
        map.put("b", 2);
        byte[] b = MAPPER.writeValueAsBytes(map);
        _verifyBytes(b, EXP);
    }
    
    public void testShortText() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeString("");
        gen.close();
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_EMPTY_STRING);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeString("abc");
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (CBORConstants.PREFIX_TYPE_TEXT + 3),
                (byte) 'a', (byte) 'b', (byte) 'c');
    }
    
    public void testLongerText() throws Exception
    {
        // First, something with 8-bit length
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        final String SHORT_ASCII = generateAsciiString(240);
        gen.writeString(SHORT_ASCII);
        gen.close();
        byte[] b = SHORT_ASCII.getBytes("UTF-8");
        int len = b.length;
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 24), (byte) len, b);

        // and ditto with fuller Unicode
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        final String SHORT_UNICODE = generateUnicodeString(160);
        gen.writeString(SHORT_UNICODE);
        gen.close();
        b = SHORT_UNICODE.getBytes("UTF-8");
        len = b.length;
        // just a sanity check; will break if generation changes
        assertEquals(196, len);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 24), (byte) len, b);

        // and then something bit more sizable
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        final String MEDIUM_UNICODE = generateUnicodeString(800);
        gen.writeString(MEDIUM_UNICODE);
        gen.close();
        b = MEDIUM_UNICODE.getBytes("UTF-8");
        len = b.length;
        // just a sanity check; will break if generation changes
        assertEquals(926, len);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 25),
                (byte) (len>>8), (byte) len,
                b);
    }

    public void testInvalidWrites() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartObject();
        // this should NOT succeed:
        try {
            gen.writeString("test");
            fail("Should NOT allow write of anything but FIELD_NAME or END_OBJECT at this point");
        } catch (JsonGenerationException e) {
            verifyException(e, "expecting field name");
        }
        gen.close();

        // and as per [dataformat-cbor#21] this also
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeStartArray();
        gen.writeStartObject();
        try {
            gen.writeString("BAR");
            fail("Should NOT allow write of anything but FIELD_NAME or END_OBJECT at this point");
        } catch (JsonGenerationException e) {
            verifyException(e, "expecting field name");
        }
        gen.close();
    }

    public void testCopyCurrentEventWithTag() throws Exception {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(BigDecimal.ONE);
        sourceGen.close();

        final ByteArrayOutputStream targetBytes = new ByteArrayOutputStream();
        final CBORGenerator gen = cborGenerator(targetBytes);
        final CBORParser cborParser = cborParser(sourceBytes);
        while (cborParser.nextToken() != null) {
            gen.copyCurrentEvent(cborParser);
        }
        gen.close();

        // copyCurrentEvent doesn't preserve fixed arrays, so we can't
        // compare with the source bytes.
        Assert.assertArrayEquals(new byte[] {
                CBORConstants.BYTE_TAG_BIGFLOAT,
                CBORConstants.BYTE_ARRAY_INDEFINITE,
                0,
                1,
                CBORConstants.BYTE_BREAK
            },
            targetBytes.toByteArray());
    }

    public void testCopyCurrentSturctureWithTag() throws Exception {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(BigDecimal.ONE);
        sourceGen.close();

        final ByteArrayOutputStream targetBytes = new ByteArrayOutputStream();
        final CBORGenerator gen = cborGenerator(targetBytes);
        final CBORParser cborParser = cborParser(sourceBytes);
        cborParser.nextToken();
        gen.copyCurrentStructure(cborParser);
        gen.close();

        // copyCurrentEvent doesn't preserve fixed arrays, so we can't
        // compare with the source bytes.
        Assert.assertArrayEquals(new byte[] {
                CBORConstants.BYTE_TAG_BIGFLOAT,
                CBORConstants.BYTE_ARRAY_INDEFINITE,
                0,
                1,
                CBORConstants.BYTE_BREAK
            },
            targetBytes.toByteArray());
    }
}
