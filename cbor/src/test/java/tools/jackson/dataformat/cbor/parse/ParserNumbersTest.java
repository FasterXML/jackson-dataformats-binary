package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import tools.jackson.core.*;
import tools.jackson.core.JsonParser.NumberType;
import tools.jackson.dataformat.cbor.*;
import tools.jackson.dataformat.cbor.testutil.ThrottledInputStream;

@SuppressWarnings("resource")
public class ParserNumbersTest extends CBORTestBase
{
    public void testIntValues() throws Exception
    {
        // first, single-byte
        _verifyInt(13);
        _verifyInt(-19);
        // two bytes
        _verifyInt(255);
        _verifyInt(-127);
        // three
        _verifyInt(256);
        _verifyInt(0xFFFF);
        _verifyInt(-300);
        _verifyInt(-0xFFFF);
        // and all 4 bytes
        _verifyInt(0x7FFFFFFF);
        _verifyInt(-0x7FFF0002);
        _verifyInt(0x70000000 << 1);
    }

    private void _verifyInt(int value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(out);
        gen.writeNumber(value);
        gen.close();

        JsonParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(value, p.getIntValue());
        assertEquals((double) value, p.getDoubleValue());
        assertEquals(BigInteger.valueOf(value), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(value), p.getDecimalValue());
        assertNull(p.nextToken());
        p.close();

        // also check that skipping works
        p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // and finally that throttled test works
        p = cborParser(new ThrottledInputStream(out.toByteArray(), 1));
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    // Special tests for "gray area" for uint32 values that do not fit
    // in Java int; from [dataformats-binary#30]
    public void testInt32Overflow() throws Exception
    {
        // feed in max uint32, which is 2x+1 as big as Integer.MAX_VALUE
        byte[] input = new byte[] {
               (byte) CBORConstants.PREFIX_TYPE_INT_POS + 26, // uint32, that is, 4 more bytes
               -1, -1, -1, -1
        };
        CBORParser p = cborParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // should be exposed as `long` because these uint32 values do not fit in Java `int`
        assertEquals(0xFFFFFFFFL, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        p.close();

        // and then the reverse; something that ought to be negative
        input = new byte[] {
                (byte) CBORConstants.PREFIX_TYPE_INT_NEG + 26, // int32, that is, 4 more bytes
                (byte) 0x80, 0, 0, 0
        };
        p = cborParser(input);
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

    public void testLongValues() throws Exception
    {
        _verifyLong(1L + Integer.MAX_VALUE);
        _verifyLong(Long.MIN_VALUE);
        _verifyLong(Long.MAX_VALUE);
        _verifyLong(-1L + Integer.MIN_VALUE);
    }

    private void _verifyLong(long value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(value, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals((double) value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();

        // also check that skipping works
        p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // and finally that throttled test works
        p = cborParser(new ThrottledInputStream(out.toByteArray(), 1));
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }


    // Special tests for "gray area" for uint64 values that do not fit
    // in Java long; from [dataformats-binary#30]
    public void testInt64Overflow() throws Exception
    {
        // feed in max uint64, which is 2x+1 as big as Long.MAX_VALUE
        byte[] input = new byte[] {
               (byte) CBORConstants.PREFIX_TYPE_INT_POS + 27, // uint64, that is, 8 more bytes
               -1, -1, -1, -1, -1, -1, -1, -1
        };
        CBORParser p = cborParser(input);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // should be exposed as BigInteger
        assertEquals(NumberType.BIG_INTEGER, p.getNumberType());
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
        p = cborParser(input);
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

    public void testDoubleValues() throws Exception
    {
        _verifyDouble(0.25, false);
        _verifyDouble(20.5, false);
        _verifyDouble(Double.NaN, true);
        _verifyDouble(Double.POSITIVE_INFINITY, true);
        _verifyDouble(Double.NEGATIVE_INFINITY, true);
        _verifyDouble(-5000.25, false);
    }

    private void _verifyDouble(double value, boolean isNaN) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        if (NumberType.DOUBLE != p.getNumberType()) {
            fail("Expected `NumberType.DOUBLE`, got "+p.getNumberType()+": "+p.getText());
        }
        assertEquals(value, p.getDoubleValue());
        assertEquals(isNaN, p.isNaN());
        assertEquals((float) value, p.getFloatValue());

        assertNull(p.nextToken());

        // also skip
        p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextToken());

        p.close();
    }

    public void testFloatValues() throws Exception
    {
        // first, single-byte
        _verifyFloat(0.25, false);
        _verifyFloat(20.5, false);
        _verifyFloat(Float.NaN, true);
        _verifyFloat(Float.POSITIVE_INFINITY, true);
        _verifyFloat(Float.NEGATIVE_INFINITY, true);
        _verifyFloat(-5000.25, false);

        // But then, oddity: 16-bit mini-float
        // Examples from [https://en.wikipedia.org/wiki/Half_precision_floating-point_format]
        _verifyHalfFloat(0, 0.0);
        _verifyHalfFloat(0x3C00, 1.0);
        _verifyHalfFloat(0xC000, -2.0);
        _verifyHalfFloat(0x7BFF, 65504.0);
        _verifyHalfFloat(0x7C00, Double.POSITIVE_INFINITY);
        _verifyHalfFloat(0xFC00, Double.NEGATIVE_INFINITY);

        // ... can add more, but need bit looser comparison if so
    }

    private void _verifyFloat(double value, boolean isNaN) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(out);
        gen.writeNumber((float) value);
        gen.close();
        JsonParser p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        if (NumberType.FLOAT != p.getNumberType()) {
            fail("Expected `NumberType.FLOAT`, got "+p.getNumberType()+": "+p.getText());
        }
        assertEquals((float) value, p.getFloatValue());
        assertEquals(isNaN, p.isNaN());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());

        // also skip
        p = cborParser(out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextToken());

        p.close();
    }

    private void _verifyHalfFloat(int i16, double value) throws IOException
    {
        byte[] data = new byte[] {
                (byte) (CBORConstants.PREFIX_TYPE_MISC + 25),
                (byte) (i16 >> 8), (byte) i16
        };
        boolean expNaN = Double.isNaN(value) || Double.isInfinite(value);
        JsonParser p = cborParser(data);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(expNaN, p.isNaN());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();

        // should be skippable too
        p = cborParser(data);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    public void testFloatNumberType() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeStartObject();
        generator.writeName("foo");
        generator.writeNumber(3f);
        generator.writeEndObject();
        generator.close();

        CBORParser parser = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(NumberType.FLOAT, parser.getNumberType());
        assertEquals(3f, parser.getFloatValue());
        assertEquals(3d, parser.getDoubleValue());
        assertEquals(3, parser.getIntValue());
        assertEquals(3, parser.getLongValue());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    public void testBigDecimalType() throws IOException {
        final BigDecimal NR = new BigDecimal("172.125");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeNumber(NR);
        generator.close();

        final byte[] b = out.toByteArray();
        try (CBORParser parser = cborParser(b)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, parser.getNumberType());
            assertEquals(NR, parser.getDecimalValue());
            assertEquals(NR.doubleValue(), parser.getDoubleValue());
            assertEquals(NR.intValue(), parser.getIntValue());
            assertNull(parser.nextToken());
        }
    }

    public void testBigDecimalType2() throws IOException {
        // Almost good. But [dataformats#139] to consider too, see
        // [https://tools.ietf.org/html/rfc7049#section-2.4.2]
        final byte[] spec = new byte[] {
                (byte) 0xC4,  // tag 4
                (byte) 0x82,  // Array of length 2
                0x21,  // int -- -2
                0x19, 0x6a, (byte) 0xb3 // int 27315
        };
        try (CBORParser parser = cborParser(spec)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, parser.getNumberType());
            assertEquals(new BigDecimal("273.15"), parser.getDecimalValue());
            assertNull(parser.nextToken());
        }
    }
}
