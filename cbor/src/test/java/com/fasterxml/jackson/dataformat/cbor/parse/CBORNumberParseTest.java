package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonParser.NumberTypeFP;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.dataformat.cbor.*;
import com.fasterxml.jackson.dataformat.cbor.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("resource")
public class CBORNumberParseTest extends CBORTestBase
{
    private final CBORFactory CBOR_F = cborFactory();

    @Test
    public void testIntValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyInt(f, 13);
        _verifyInt(f, -19);
        // two bytes
        _verifyInt(f, 255);
        _verifyInt(f, -127);
        // three
        _verifyInt(f, 256);
        _verifyInt(f, 0xFFFF);
        _verifyInt(f, -300);
        _verifyInt(f, -0xFFFF);
        // and all 4 bytes
        _verifyInt(f, 0x7FFFFFFF);
        _verifyInt(f, -0x7FFF0002);
        _verifyInt(f, 0x70000000 << 1);
    }

    private void _verifyInt(CBORFactory f, int value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();

        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(NumberTypeFP.UNKNOWN, p.getNumberTypeFP());
        assertEquals(value, p.getIntValue());
        assertEquals((double) value, p.getDoubleValue());
        assertEquals(BigInteger.valueOf(value), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(value), p.getDecimalValue());
        assertNull(p.nextToken());
        p.close();

        // also check that skipping works
        p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // and finally that throttled test works
        p = cborParser(f, new ThrottledInputStream(out.toByteArray(), 1));
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    // Special tests for "gray area" for uint32 values that do not fit
    // in Java int; from [dataformats-binary#30]
    @Test
    public void testInt32Overflow() throws Exception
    {
        // feed in max uint32, which is 2x+1 as big as Integer.MAX_VALUE
        byte[] input = new byte[] {
               (byte) CBORConstants.PREFIX_TYPE_INT_POS + 26, // uint32, that is, 4 more bytes
               -1, -1, -1, -1
        };
        CBORParser p = CBOR_F.createParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // should be exposed as `long` because these uint32 values do not fit in Java `int`
        assertEquals(0xFFFFFFFFL, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals(NumberTypeFP.UNKNOWN, p.getNumberTypeFP());
        p.close();

        // and then the reverse; something that ought to be negative
        input = new byte[] {
                (byte) CBORConstants.PREFIX_TYPE_INT_NEG + 26, // int32, that is, 4 more bytes
                (byte) 0x80, 0, 0, 0
        };
        p = CBOR_F.createParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // should be exposed as `long` because this value won't fit in `int` either
        long exp = -1L + Integer.MIN_VALUE;
        assertEquals(exp, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        p.close();

        // and, combined, a negative number where the mantissa overflows a signed int32
        input = new byte[] {
                (byte) CBORConstants.PREFIX_TYPE_INT_NEG + 26, // int32, that is, 4 more bytes
                -1, -1, -1, -1
        };
        p = cborParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(-1L - 0xFFFFFFFFL, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        p.close();
    }

    @Test
    public void testLongValues() throws Exception
    {
        _verifyLong(CBOR_F, 1L + Integer.MAX_VALUE);
        _verifyLong(CBOR_F, Long.MIN_VALUE);
        _verifyLong(CBOR_F, Long.MAX_VALUE);
        _verifyLong(CBOR_F, -1L + Integer.MIN_VALUE);
    }

    private void _verifyLong(CBORFactory f, long value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberTypeFP.UNKNOWN, p.getNumberTypeFP());
        assertEquals(value, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals((double) value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();

        // also check that skipping works
        p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // and finally that throttled test works
        p = cborParser(f, new ThrottledInputStream(out.toByteArray(), 1));
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }


    // Special tests for "gray area" for uint64 values that do not fit
    // in Java long; from [dataformats-binary#30]
    @Test
    public void testInt64Overflow() throws Exception
    {
        // feed in max uint64, which is 2x+1 as big as Long.MAX_VALUE
        byte[] input = new byte[] {
               (byte) CBORConstants.PREFIX_TYPE_INT_POS + 27, // uint64, that is, 8 more bytes
               -1, -1, -1, -1, -1, -1, -1, -1
        };
        CBORParser p = CBOR_F.createParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // should be exposed as BigInteger
        assertEquals(NumberType.BIG_INTEGER, p.getNumberType());
        assertEquals(NumberTypeFP.UNKNOWN, p.getNumberTypeFP());
        BigInteger exp = BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(1)
                .add(BigInteger.ONE);
        assertEquals(exp, p.getBigIntegerValue());
        p.close();

        // and then the reverse; something that ought to be negative
        input = new byte[] {
                (byte) CBORConstants.PREFIX_TYPE_INT_NEG + 27,
                (byte) 0x80, 0, 0, 0,
                0, 0, 0, 0
        };
        p = CBOR_F.createParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // should be exposed as `long` because this value won't fit in `int` either
        exp = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
        assertEquals(exp, p.getBigIntegerValue());
        assertEquals(NumberType.BIG_INTEGER, p.getNumberType());
        p.close();

        // and, combined, a negative number where the mantissa overflows a signed int32
        input = new byte[] {
                (byte) CBORConstants.PREFIX_TYPE_INT_NEG + 27, // int32, that is, 4 more bytes
                -1, -1, -1, -1, -1, -1, -1, -1
        };
        p = cborParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        exp = BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(1)
                .add(BigInteger.ONE)
                .negate()
                .subtract(BigInteger.ONE);
        assertEquals(exp, p.getBigIntegerValue());
        assertEquals(NumberType.BIG_INTEGER, p.getNumberType());
        p.close();
    }

    @Test
    public void testDoubleValues() throws Exception
    {
        _verifyDouble(CBOR_F, 0.25, false);
        _verifyDouble(CBOR_F, 20.5, false);
        _verifyDouble(CBOR_F, Double.NaN, true);
        _verifyDouble(CBOR_F, Double.POSITIVE_INFINITY, true);
        _verifyDouble(CBOR_F, Double.NEGATIVE_INFINITY, true);
        _verifyDouble(CBOR_F, -5000.25, false);
    }

    private void _verifyDouble(CBORFactory f, double value, boolean isNaN) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        if (NumberType.DOUBLE != p.getNumberType()) {
            fail("Expected `NumberType.DOUBLE`, got "+p.getNumberType()+": "+p.getText());
        }
        assertEquals(NumberTypeFP.DOUBLE64, p.getNumberTypeFP());
        assertEquals(value, p.getDoubleValue());
        assertEquals(isNaN, p.isNaN());
        assertEquals((float) value, p.getFloatValue());

        assertNull(p.nextToken());

        // also skip
        p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextToken());

        p.close();
    }

    @Test
    public void testFloatValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyFloat(f, 0.25, false);
        _verifyFloat(f, 20.5, false);
        _verifyFloat(CBOR_F, Float.NaN, true);
        _verifyFloat(CBOR_F, Float.POSITIVE_INFINITY, true);
        _verifyFloat(CBOR_F, Float.NEGATIVE_INFINITY, true);
        _verifyFloat(f, -5000.25, false);

        // But then, oddity: 16-bit mini-float
        // Examples from [https://en.wikipedia.org/wiki/Half_precision_floating-point_format]
        _verifyHalfFloat(f, 0, 0.0);
        _verifyHalfFloat(f, 0x3C00, 1.0);
        _verifyHalfFloat(f, 0xC000, -2.0);
        _verifyHalfFloat(f, 0x7BFF, 65504.0);
        _verifyHalfFloat(f, 0x7C00, Double.POSITIVE_INFINITY);
        _verifyHalfFloat(f, 0xFC00, Double.NEGATIVE_INFINITY);

        // ... can add more, but need bit looser comparison if so
    }

    private void _verifyFloat(CBORFactory f, double value, boolean isNaN) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber((float) value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        if (NumberType.FLOAT != p.getNumberType()) {
            fail("Expected `NumberType.FLOAT`, got "+p.getNumberType()+": "+p.getText());
        }
        assertEquals(NumberTypeFP.FLOAT32, p.getNumberTypeFP());
        assertEquals((float) value, p.getFloatValue());
        assertEquals(isNaN, p.isNaN());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());

        // also skip
        p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextToken());

        p.close();
    }

    private void _verifyHalfFloat(JsonFactory f, int i16, double value) throws IOException
    {
        byte[] data = new byte[] {
                (byte) (CBORConstants.PREFIX_TYPE_MISC + 25),
                (byte) (i16 >> 8), (byte) i16
        };

        boolean expNaN = Double.isNaN(value) || Double.isInfinite(value);

        JsonParser p = f.createParser(data);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(expNaN, p.isNaN());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        // Alas, while we have FP type (FLOAT16), not yet connected to this
        assertEquals(NumberTypeFP.FLOAT32, p.getNumberTypeFP());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();

        // should be skippable too
        p = f.createParser(data);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testFloatNumberType() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeStartObject();
        generator.writeFieldName("foo");
        generator.writeNumber(3f);
        generator.writeEndObject();
        generator.close();

        CBORParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertEquals(NumberTypeFP.FLOAT32, p.getNumberTypeFP());
        assertEquals(3f, p.getFloatValue());
        assertEquals(3d, p.getDoubleValue());
        assertEquals(3, p.getIntValue());
        assertEquals(3, p.getLongValue());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    @Test
    public void testBigDecimalType() throws IOException {
        final BigDecimal NR = new BigDecimal("172.125");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeNumber(NR);
        generator.close();

        final byte[] b = out.toByteArray();
        try (CBORParser p = cborParser(b)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, p.getNumberType());
            assertEquals(NumberTypeFP.BIG_DECIMAL, p.getNumberTypeFP());
            assertEquals(NR, p.getDecimalValue());
            assertEquals(NR.doubleValue(), p.getDoubleValue());
            assertEquals(NR.intValue(), p.getIntValue());
            assertNull(p.nextToken());
        }
    }

    @Test
    public void testBigDecimalType2() throws IOException {
        // Almost good. But [dataformats#139] to consider too, see
        // [https://tools.ietf.org/html/rfc7049#section-2.4.2]
        final byte[] spec = new byte[] {
                (byte) 0xC4,  // tag 4
                (byte) 0x82,  // Array of length 2
                0x21,  // int -- -2
                0x19, 0x6a, (byte) 0xb3 // int 27315
        };
        try (CBORParser p = cborParser(spec)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, p.getNumberType());
            assertEquals(NumberTypeFP.BIG_DECIMAL, p.getNumberTypeFP());
            assertEquals(new BigDecimal("273.15"), p.getDecimalValue());
            assertNull(p.nextToken());
        }
    }

    @Test
    public void testVeryBigDecimalType() throws IOException {
        final int len = 10000;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(1);
        }
        final BigDecimal NR = new BigDecimal(sb.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeNumber(NR);
        generator.close();

        final byte[] b = out.toByteArray();
        try (CBORParser parser = cborParser(b)) {
            try {
                parser.nextToken();
                fail("expected StreamConstraintsException");
            } catch (StreamConstraintsException e) {
                assertTrue(e.getMessage().startsWith("Number value length (4153) exceeds the maximum allowed"),
                        "unexpected exception message: " + e.getMessage());
            }
        }
    }

    @Test
    public void testVeryBigDecimalWithUnlimitedNumLength() throws IOException {
        final int len = 10000;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(1);
        }
        final BigDecimal NR = new BigDecimal(sb.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeNumber(NR);
        generator.close();

        final byte[] b = out.toByteArray();
        CBORFactoryBuilder f = cborFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(Integer.MAX_VALUE).build());
        try (CBORParser parser = cborParser(f.build(), b)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, parser.getNumberType());
            assertEquals(NR, parser.getDecimalValue());
            assertEquals(NR.doubleValue(), parser.getDoubleValue());
            assertNull(parser.nextToken());
        }
    }
}
