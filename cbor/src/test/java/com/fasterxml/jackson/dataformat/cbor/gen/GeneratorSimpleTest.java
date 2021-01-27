package com.fasterxml.jackson.dataformat.cbor.gen;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORConstants;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

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

        assertEquals(0, gen.streamWriteOutputBuffered());
        gen.writeBoolean(true);
        assertEquals(1, gen.streamWriteOutputBuffered());
        
        gen.close();
        assertEquals(0, gen.streamWriteOutputBuffered());
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
        gen.writeNumber((long) Integer.MAX_VALUE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 26),
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNumber((long) Integer.MIN_VALUE);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 26),
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    }

    // [dataformats-binary#201]
    public void testMinimalIntValues2() throws Exception
    {
        ByteArrayOutputStream out;
        CBORGenerator gen;

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        // -1 if coerced as int, BUT cbor encoding can fit in 32-bit integer since
        // sign is indicated by prefix
        gen.writeNumber(0xffffffffL);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 26),
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        // similarly for negative numbers, we have 32-bit value bits, not 31
        // as with Java int
        gen.writeNumber(-0xffffffffL - 1);
        gen.close();
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 26),
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
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

    public void testLongValues()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);

        // 07-Apr-2020, tatu: Since minimization enabled by default,
        //   need to use value that can not be minimized
        long l = ((long) Integer.MIN_VALUE) << 2;
        gen.writeNumber(l);
        gen.close();
        byte[] b = out.toByteArray();
        assertEquals(9, b.length);
        assertEquals((byte) (CBORConstants.PREFIX_TYPE_INT_NEG + 27), b[0]);
        // could test full contents, but for now this shall suffice
        assertEquals(0, b[1]);
        assertEquals(0, b[2]);
        assertEquals(0, b[3]);
    }
    
    public void testFloatValues()
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

    // [dataformats-binary#139]: wrong encoding of BigDecimal
    public void testBigDecimalValues()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        final BigDecimal NR = new BigDecimal("273.15");
        gen.writeNumber(NR);
        gen.close();
        byte[] b = out.toByteArray();

        // [https://tools.ietf.org/html/rfc7049#section-2.4.2]
        final byte[] spec = new byte[] {
                (byte) 0xC4,  // tag 4
                (byte) 0x82,  // Array of length 2
                0x21,  // int -- -2
                0x19, 0x6a, (byte) 0xb3 // int 27315
        };
        assertEquals(spec.length, b.length);
        Assert.assertArrayEquals(spec, b);
    }
    
    public void testEmptyArray()
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

    public void testEmptyObject()
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
    
    public void testIntArray()
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

        byte[] EXP = new byte[] {
                CBORConstants.BYTE_ARRAY_INDEFINITE,
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 1),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 2),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 3),
                CBORConstants.BYTE_BREAK
        };
        
        _verifyBytes(out.toByteArray(), EXP);

        // Data-binding should actually use fixed-length
        EXP = new byte[] {
                (byte) (CBORConstants.PREFIX_TYPE_ARRAY + 3),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 1),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 2),
                (byte) (CBORConstants.PREFIX_TYPE_INT_POS + 3),
        };

        byte[] b = MAPPER.writeValueAsBytes(new int[] { 1, 2, 3 });
        _verifyBytes(b, EXP);
    }

    public void testTrivialObject()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        
        // currently will produce indefinite-length Object
        gen.writeStartObject();
        gen.writeNumberProperty("a", 1);
        gen.writeNumberProperty("b", 2);
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

    public void testLongerText()
    {
        // First, something with 8-bit length
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        final String SHORT_ASCII = generateLongAsciiString(240);
        gen.writeString(SHORT_ASCII);
        gen.close();
        byte[] b = SHORT_ASCII.getBytes(StandardCharsets.UTF_8);
        int len = b.length;
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 24), (byte) len, b);

        // and ditto with fuller Unicode
        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        final String SHORT_UNICODE = generateUnicodeString(160);
        gen.writeString(SHORT_UNICODE);
        gen.close();
        b = SHORT_UNICODE.getBytes(StandardCharsets.UTF_8);
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
        b = MEDIUM_UNICODE.getBytes(StandardCharsets.UTF_8);
        len = b.length;
        // just a sanity check; will break if generation changes
        assertEquals(926, len);
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + 25),
                (byte) (len>>8), (byte) len,
                b);
    }

    public void testInvalidWrites()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartObject();
        // this should NOT succeed:
        try {
            gen.writeString("test");
            fail("Should NOT allow write of anything but FIELD_NAME or END_OBJECT at this point");
        } catch (StreamWriteException e) {
            verifyException(e, "expecting a property name");
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
        } catch (StreamWriteException e) {
            verifyException(e, "expecting a property name");
        }
        gen.close();
    }

    public void testCopyCurrentEventWithTag() {
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
        cborParser.close();

        // copyCurrentEvent doesn't preserve fixed arrays, so we can't
        // compare with the source bytes.
        Assert.assertArrayEquals(new byte[] {
                CBORConstants.BYTE_TAG_DECIMAL_FRACTION,
                CBORConstants.BYTE_ARRAY_2_ELEMENTS,
                0,
                1,
            },
            targetBytes.toByteArray());
    }

    public void testCopyCurrentStructureWithTaggedArray()
    {
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
        cborParser.close();

        // copyCurrentEvent doesn't preserve fixed arrays, so we can't
        // compare with the source bytes.
        Assert.assertArrayEquals(new byte[] {
                CBORConstants.BYTE_TAG_DECIMAL_FRACTION,
                CBORConstants.BYTE_ARRAY_2_ELEMENTS,
                0,
                1,
            },
            targetBytes.toByteArray());
    }


    public void testCopyCurrentStructureWithTaggedBinary()
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(BigInteger.ZERO);
        sourceGen.close();

        final ByteArrayOutputStream targetBytes = new ByteArrayOutputStream();
        final CBORGenerator gen = cborGenerator(targetBytes);
        final CBORParser cborParser = cborParser(sourceBytes);
        cborParser.nextToken();
        gen.copyCurrentStructure(cborParser);
        gen.close();
        cborParser.close();

        Assert.assertArrayEquals(
            sourceBytes.toByteArray(),
            targetBytes.toByteArray());
    }
}
