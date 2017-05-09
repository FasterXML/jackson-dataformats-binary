package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.dataformat.smile.*;

public class SimpleArrayTest extends AsyncTestBase
{
    private final SmileFactory F_REQ_HEADERS = new SmileFactory();
    {
        F_REQ_HEADERS.enable(SmileParser.Feature.REQUIRE_HEADER);
    }

    private final SmileFactory F_NO_HEADERS = new SmileFactory();
    {
        F_REQ_HEADERS.disable(SmileParser.Feature.REQUIRE_HEADER);
    }
    
    public void testBooleans() throws IOException
    {
        byte[] data = _smileDoc("[ true, false, true, true, false ]", true);

        // first: require headers, no offsets
        SmileFactory f = F_REQ_HEADERS;
        _testBooleans(f, data, 0, 100);
        _testBooleans(f, data, 0, 3);
        _testBooleans(f, data, 0, 1);

        // then with some offsets:
        _testBooleans(f, data, 1, 100);
        _testBooleans(f, data, 1, 3);
        _testBooleans(f, data, 1, 1);

        // also similar but without header
        data = _smileDoc("[ true, false, true, true, false ]", true);
        f = F_NO_HEADERS;
        _testBooleans(f, data, 0, 100);
        _testBooleans(f, data, 0, 3);
        _testBooleans(f, data, 0, 1);

        _testBooleans(f, data, 1, 100);
        _testBooleans(f, data, 1, 3);
        _testBooleans(f, data, 1, 1);
    }

    private void _testBooleans(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());
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
        SmileFactory f = F_REQ_HEADERS;
        _testInts(f, input, data, 0, 100);
        _testInts(f, input, data, 0, 3);
        _testInts(f, input, data, 0, 1);

        _testInts(f, input, data, 1, 100);
        _testInts(f, input, data, 1, 3);
        _testInts(f, input, data, 1, 1);
    }

    private void _testInts(SmileFactory f, int[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
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
        SmileFactory f = F_REQ_HEADERS;
        JsonGenerator g = f.createGenerator(bytes);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testLong(f, input, data, 0, 100);
        _testLong(f, input, data, 0, 3);
        _testLong(f, input, data, 0, 1);

        _testLong(f, input, data, 1, 100);
        _testLong(f, input, data, 1, 3);
        _testLong(f, input, data, 1, 1);
    }

    private void _testLong(SmileFactory f, long[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
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

    public void testFloats() throws IOException
    {
        final float[] input = new float[] { 0.0f, 0.25f, -0.5f, 10000.125f, - 99999.075f };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        SmileFactory f = F_REQ_HEADERS;
        JsonGenerator g = f.createGenerator(bytes);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testFloats(f, input, data, 0, 100);
        _testFloats(f, input, data, 0, 3);
        _testFloats(f, input, data, 0, 1);

        _testFloats(f, input, data, 1, 100);
        _testFloats(f, input, data, 1, 3);
        _testFloats(f, input, data, 1, 1);
    }

    private void _testFloats(SmileFactory f, float[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
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
        SmileFactory f = F_REQ_HEADERS;
        JsonGenerator g = f.createGenerator(bytes);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeNumber(input[i]);
        }
        g.writeEndArray();
        g.close();
        byte[] data = bytes.toByteArray();
        _testDoubles(f, input, data, 0, 100);
        _testDoubles(f, input, data, 0, 3);
        _testDoubles(f, input, data, 0, 1);

        _testDoubles(f, input, data, 1, 100);
        _testDoubles(f, input, data, 1, 3);
        _testDoubles(f, input, data, 1, 1);
    }

    private void _testDoubles(SmileFactory f, double[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
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
}
