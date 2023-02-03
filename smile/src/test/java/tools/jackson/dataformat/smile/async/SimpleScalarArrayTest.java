package tools.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import tools.jackson.core.*;
import tools.jackson.core.JsonParser.NumberType;
import tools.jackson.databind.ObjectReader;

public class SimpleScalarArrayTest extends AsyncTestBase
{
    private final ObjectReader REQ_HEADERS = _smileReader(true); // require headers
    private final ObjectReader NO_HEADERS = _smileReader(false); // do not require headers

    /*
    /**********************************************************************
    /* Boolean, int, long tests
    /**********************************************************************
     */

    public void testBooleans() throws IOException
    {
        byte[] data = _smileDoc("[ true, false, true, true, false ]", true);

        // first: require headers, no offsets
        ObjectReader r = REQ_HEADERS;

        _testBooleans(r, data, 0, 100);
        _testBooleans(r, data, 0, 3);
        _testBooleans(r, data, 0, 1);

        // then with some offsets:
        _testBooleans(r, data, 1, 100);
        _testBooleans(r, data, 1, 3);
        _testBooleans(r, data, 1, 1);

        // also similar but without header
        data = _smileDoc("[ true, false, true, true, false ]", false);
        r = NO_HEADERS;

        _testBooleans(r, data, 0, 100);
        _testBooleans(r, data, 0, 3);
        _testBooleans(r, data, 0, 1);

        _testBooleans(r, data, 1, 100);
        _testBooleans(r, data, 1, 3);
        _testBooleans(r, data, 1, 1);
    }

    private void _testBooleans(ObjectReader or,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());

        assertEquals("false", r.currentText());
        assertEquals("false", r.currentTextViaCharacters());

        assertToken(JsonToken.END_ARRAY, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    public void testInts() throws IOException
    {
        final int[] input = new int[] { 1, -1, 16, -17, 131, -155, 1000, -3000, 0xFFFF, -99999,
                Integer.MIN_VALUE, 0, Integer.MAX_VALUE };
        StringBuilder sb = new StringBuilder().append("[");
        for (int i = 0; i < input.length; ++i) {
            if (i > 0) sb.append(',');
            sb.append(input[i]);
        }
        byte[] data = _smileDoc(sb.append(']').toString(), true);
        ObjectReader r = REQ_HEADERS;
        _testInts(r, input, data, 0, 100);
        _testInts(r, input, data, 0, 3);
        _testInts(r, input, data, 0, 1);

        _testInts(r, input, data, 1, 100);
        _testInts(r, input, data, 1, 3);
        _testInts(r, input, data, 1, 1);
    }

    private void _testInts(ObjectReader or, int[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
            assertEquals(values[i], r.getIntValue());
            assertEquals(NumberType.INT, r.getNumberType());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    public void testLong() throws IOException
    {
        final long[] input = new long[] {
                // SmileGenerator will try to minimize so....
//                1, -1, 16, -17, 131, -155, 1000, -3000, 0xFFFF, -99999,
                -1L + Integer.MIN_VALUE, 1L + Integer.MAX_VALUE,
                19L * Integer.MIN_VALUE, 27L * Integer.MAX_VALUE,
                Long.MIN_VALUE, Long.MAX_VALUE };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        ObjectReader r = REQ_HEADERS;
        JsonGenerator g = _smileGenerator(bytes, true);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testLong(r, input, data, 0, 100);
        _testLong(r, input, data, 0, 3);
        _testLong(r, input, data, 0, 1);

        _testLong(r, input, data, 1, 100);
        _testLong(r, input, data, 1, 3);
        _testLong(r, input, data, 1, 1);
    }

    private void _testLong(ObjectReader or, long[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
            assertEquals(values[i], r.getLongValue());
            assertEquals(NumberType.LONG, r.getNumberType());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    /*
    /**********************************************************************
    /* Floating point
    /**********************************************************************
     */

    public void testFloats() throws IOException
    {
        final float[] input = new float[] { 0.0f, 0.25f, -0.5f, 10000.125f, - 99999.075f };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        ObjectReader r = REQ_HEADERS;
        JsonGenerator g = _smileGenerator(bytes, true);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testFloats(r, input, data, 0, 100);
        _testFloats(r, input, data, 0, 3);
        _testFloats(r, input, data, 0, 1);

        _testFloats(r, input, data, 1, 100);
        _testFloats(r, input, data, 1, 3);
        _testFloats(r, input, data, 1, 1);
    }

    private void _testFloats(ObjectReader or, float[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
            assertEquals(values[i], r.getFloatValue());
            assertEquals(NumberType.FLOAT, r.getNumberType());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());
        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    public void testDoubles() throws IOException
    {
        final double[] input = new double[] { 0.0, 0.25, -0.5, 10000.125, -99999.075 };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        ObjectReader r = REQ_HEADERS;
        JsonGenerator g = _smileGenerator(bytes, true);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testDoubles(r, input, data, 0, 100);
        _testDoubles(r, input, data, 0, 3);
        _testDoubles(r, input, data, 0, 1);

        _testDoubles(r, input, data, 1, 100);
        _testDoubles(r, input, data, 1, 3);
        _testDoubles(r, input, data, 1, 1);
    }

    private void _testDoubles(ObjectReader or, double[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
            assertEquals(values[i], r.getDoubleValue());
            assertEquals(NumberType.DOUBLE, r.getNumberType());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    /*
    /**********************************************************************
    /* BigInteger, BigDecimal
    /**********************************************************************
     */

    public void testBigIntegers() throws IOException
    {
        BigInteger bigBase = BigInteger.valueOf(1234567890344656736L);
        final BigInteger[] input = new BigInteger[] {
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.TEN,
                BigInteger.valueOf(-999L),
                bigBase,
                bigBase.shiftLeft(100).add(BigInteger.valueOf(123456789L)),
                bigBase.add(bigBase),
                bigBase.multiply(BigInteger.valueOf(17)),
                bigBase.negate()
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        ObjectReader r = REQ_HEADERS;
        JsonGenerator g = _smileGenerator(bytes, true);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testBigIntegers(r, input, data, 0, 100);
        _testBigIntegers(r, input, data, 0, 3);
        _testBigIntegers(r, input, data, 0, 1);

        _testBigIntegers(r, input, data, 1, 100);
        _testBigIntegers(r, input, data, 2, 3);
        _testBigIntegers(r, input, data, 3, 1);
    }

    private void _testBigIntegers(ObjectReader or, BigInteger[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            BigInteger expValue = values[i];
/*
System.err.println("*** EXPECT: "+expValue+" (length: "+expValue.toByteArray().length+" bytes)");
byte[] expB = expValue.toByteArray();
for (int x = 0; x < expB.length; ++x) {
    System.err.printf(" %02x", expB[x] & 0xFF);
}
System.err.println();
*/
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
//System.err.println("*** -> got EXPECTed? "+r.getBigIntegerValue());

            assertEquals(expValue, r.getBigIntegerValue());
            assertEquals(NumberType.BIG_INTEGER, r.getNumberType());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    public void testBigDecimals() throws IOException
    {
        BigDecimal bigBase = new BigDecimal("1234567890344656736.125");
        final BigDecimal[] input = new BigDecimal[] {
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.valueOf(-999.25),
                bigBase,
                bigBase.divide(new BigDecimal("5")),
                bigBase.add(bigBase),
                bigBase.multiply(new BigDecimal("1.23")),
                bigBase.negate()
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        ObjectReader r = REQ_HEADERS;
        JsonGenerator g = _smileGenerator(bytes, true);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();

        _testBigDecimals(r, input, data, 0, 100);
        _testBigDecimals(r, input, data, 0, 3);
        _testBigDecimals(r, input, data, 0, 1);

        _testBigDecimals(r, input, data, 1, 100);
        _testBigDecimals(r, input, data, 2, 3);
        _testBigDecimals(r, input, data, 3, 1);
    }

    private void _testBigDecimals(ObjectReader or, BigDecimal[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(or, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            BigDecimal expValue = values[i];
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
            assertEquals(expValue, r.getBigDecimalValue());
            assertEquals(NumberType.BIG_DECIMAL, r.getNumberType());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

}
