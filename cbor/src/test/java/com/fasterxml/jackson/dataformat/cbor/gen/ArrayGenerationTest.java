package com.fasterxml.jackson.dataformat.cbor.gen;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic testing for scalar-array write methods added in 2.8.
 */
public class ArrayGenerationTest extends CBORTestBase
{
    private final CBORFactory FACTORY = new CBORFactory();

    @Test
    public void testIntArray() throws Exception
    {
        _testIntArray();
    }

    @Test
    public void testLongArray() throws Exception
    {
        _testLongArray();
    }

    @Test
    public void testDoubleArray() throws Exception
    {
        _testDoubleArray();
    }

    @Test
    public void testMinimalIntValuesForInt() throws Exception
    {
        // Array with 3 values, with different sizing
        _testMinimalIntValuesForInt(1, -1, 3, 11); // single-byte
        _testMinimalIntValuesForInt(200, -200, 5, 11); // two-byte (marker, 0xFF)
        _testMinimalIntValuesForInt(0xC831, -50000, 7, 11); // three-byte (marker, 0xFFFF)
        _testMinimalIntValuesForInt(0x35690001, -(0x7FFFFFF0), 11, 11); // full
    }

    private void _testMinimalIntValuesForInt(int v1, int v2,
            int minLen, int fullLen) throws Exception
    {
        final int[] input = new int[] { v1, v2 };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = FACTORY.createGenerator(bytes);
        assertTrue(gen.isEnabled(CBORGenerator.Feature.WRITE_MINIMAL_INTS));
        gen.writeArray(input, 0, 2);
        gen.close();

        // With default settings, should get:
        byte[] encoded = bytes.toByteArray();
        assertEquals(minLen, encoded.length);

        // then verify contents

        CBORParser p = FACTORY.createParser(encoded);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(input[0], p.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(input[1], p.getIntValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();

        // but then also check without minimization
        bytes = new ByteArrayOutputStream();
        gen = FACTORY.createGenerator(bytes);
        gen.disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS);

        gen.writeArray(input, 0, 2);
        gen.close();

        // With default settings, should get:
        encoded = bytes.toByteArray();
        assertEquals(fullLen, encoded.length);

        // then verify contents

        p = FACTORY.createParser(encoded);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(input[0], p.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(input[1], p.getIntValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    @Test
    public void testMinimalIntValuesForLong() throws Exception
    {
        // Array with 2 values that can't be passed as `int`s but DO fit
        // CBOR 5-byte int (sign + 32-bits)
        final long[] input = new long[] {
                0xffffffffL, // max value that fits
                -0xffffffffL - 1 // min value that fits
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = FACTORY.createGenerator(bytes);
        assertTrue(gen.isEnabled(CBORGenerator.Feature.WRITE_MINIMAL_INTS));
        gen.writeArray(input, 0, 2);
        gen.close();

        // With default settings, should get:
        byte[] encoded = bytes.toByteArray();
        assertEquals(11, encoded.length);

        // then verify contents

        CBORParser p = FACTORY.createParser(encoded);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals(input[0], p.getLongValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals(input[1], p.getLongValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();

        // but then also check without minimization
        bytes = new ByteArrayOutputStream();
        gen = FACTORY.createGenerator(bytes);
        gen.disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS);

        gen.writeArray(input, 0, 2);
        gen.close();

        // With default settings, should get:
        encoded = bytes.toByteArray();
        assertEquals(19, encoded.length);

        // then verify contents

        p = FACTORY.createParser(encoded);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals(input[0], p.getLongValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals(input[1], p.getLongValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    @Test
    public void testMinimalFloatValuesForDouble() throws Exception
    {
        // Array with 2 values, one that can be represented as a float without losing precision and
        // one that cannot.
        final double[] input = new double[] {
                1.5, // can be exactly represented as a float
                0.123456789 // must be kept as double
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = FACTORY.createGenerator(bytes);
        assertFalse(gen.isEnabled(CBORGenerator.Feature.WRITE_MINIMAL_DOUBLES));
        gen.enable(CBORGenerator.Feature.WRITE_MINIMAL_DOUBLES);
        gen.writeArray(input, 0, 2);
        gen.close();

        // With minimal doubles enabled, should get:
        byte[] encoded = bytes.toByteArray();
        assertEquals(15, encoded.length);

        // then verify contents

        CBORParser p = FACTORY.createParser(encoded);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertEquals(input[0], p.getDoubleValue());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.DOUBLE, p.getNumberType());
        assertEquals(input[1], p.getDoubleValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();

        // but then also check without minimization
        bytes = new ByteArrayOutputStream();
        gen = FACTORY.createGenerator(bytes);
        gen.disable(CBORGenerator.Feature.WRITE_MINIMAL_DOUBLES);

        gen.writeArray(input, 0, 2);
        gen.close();

        // With default settings, should get:
        encoded = bytes.toByteArray();
        assertEquals(19, encoded.length);

        // then verify contents

        p = FACTORY.createParser(encoded);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.DOUBLE, p.getNumberType());
        assertEquals(input[0], p.getDoubleValue());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.DOUBLE, p.getNumberType());
        assertEquals(input[1], p.getDoubleValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    private void _testIntArray() throws Exception {
        // first special cases of 0, 1 values
        _testIntArray(0, 0, 0);
        _testIntArray(0, 1, 1);

        _testIntArray(1, 0, 0);
        _testIntArray(1, 1, 1);

        // and then some bigger data
        _testIntArray(15, 0, 0);
        _testIntArray(15, 2, 3);
        _testIntArray(39, 0, 0);
        _testIntArray(39, 4, 0);
        _testIntArray(271, 0, 0);
        _testIntArray(271, 0, 4);
        _testIntArray(666, 0, 0);
        _testIntArray(789, 0, 4);
        _testIntArray(5009, 0, 0);
        _testIntArray(7777, 0, 1);
    }

    private void _testLongArray() throws Exception {
        // first special cases of 0, 1 values
        _testLongArray(0, 0, 0);
        _testLongArray(0, 1, 1);

        _testLongArray(1, 0, 0);
        _testLongArray(1, 1, 1);

        // and then some bigger data
        _testLongArray(15, 0, 0);
        _testLongArray(15, 2, 3);
        _testLongArray(39, 0, 0);
        _testLongArray(39, 4, 0);
        _testLongArray(271, 0, 0);
        _testLongArray(271, 0, 4);
        _testLongArray(911, 0, 0);
        _testLongArray(1121, 0, 1);
        _testLongArray(5009, 0, 0);
        _testLongArray(6110, 0, 1);
    }

    private void _testDoubleArray() throws Exception {
        // first special cases of 0, 1 values
        _testDoubleArray(0, 0, 0);
        _testDoubleArray(0, 1, 1);

        _testDoubleArray(1, 0, 0);
        _testDoubleArray(1, 1, 1);

        // and then some bigger data
        _testDoubleArray(15, 0, 0);
        _testDoubleArray(15, 2, 3);
        _testDoubleArray(39, 0, 0);
        _testDoubleArray(39, 4, 0);
        _testDoubleArray(271, 0, 0);
        _testDoubleArray(271, 0, 4);
        _testDoubleArray(744, 0, 0);
        _testDoubleArray(999, 0, 4);
        _testDoubleArray(5009, 0, 0);
        _testDoubleArray(7256, 0, 1);
    }

    private void _testIntArray(int elements, int pre, int post) throws Exception
    {
        int[] values = new int[elements+pre+post];
        for (int i = pre, end = pre+elements; i < end; ++i) {
            values[i] = i-pre;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator gen = FACTORY.createGenerator(bytes);
        gen.writeArray(values, pre, elements);
        gen.close();

        JsonParser p = FACTORY.createParser(bytes.toByteArray());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0; i < elements; ++i) {
            if ((i & 1) == 0) { // alternate
                JsonToken t = p.nextToken();
                if (t != JsonToken.VALUE_NUMBER_INT) {
                    fail("Expected number, got "+t+", element #"+i);
                }
                int act = p.getIntValue();
                if (act != i) {
                    fail("Entry #"+i+", expected "+i+", got "+act);
                }
            } else {
                assertEquals(i, p.nextIntValue(-1));
            }
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    private void _testLongArray(int elements, int pre, int post) throws Exception
    {
        long[] values = new long[elements+pre+post];
        for (int i = pre, end = pre+elements; i < end; ++i) {
            values[i] = i-pre;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator gen = FACTORY.createGenerator(bytes);
        gen.writeArray(values, pre, elements);
        gen.close();
        JsonParser p = FACTORY.createParser(bytes.toByteArray());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0; i < elements; ++i) {
            if ((i & 1) == 0) { // alternate
                JsonToken t = p.nextToken();
                if (t != JsonToken.VALUE_NUMBER_INT) {
                    fail("Expected number, got "+t+", element #"+i);
                }
                long act = p.getLongValue();
                if (act != i) {
                    fail("Entry #"+i+", expected "+i+", got "+act);
                }
            } else {
                assertEquals(i, p.nextLongValue(-1));
            }
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    private void _testDoubleArray(int elements, int pre, int post) throws Exception
    {
        double[] values = new double[elements+pre+post];
        for (int i = pre, end = pre+elements; i < end; ++i) {
            values[i] = i-pre;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator gen = FACTORY.createGenerator(bytes);
        gen.writeArray(values, pre, elements);
        gen.close();
        JsonParser p = FACTORY.createParser(bytes.toByteArray());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0; i < elements; ++i) {
            JsonToken t = p.nextToken();
            if (t != JsonToken.VALUE_NUMBER_FLOAT) {
                fail("Expected floating-point number, got "+t+", element #"+i);
            }
            assertEquals((double) i, p.getDoubleValue());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }
}
