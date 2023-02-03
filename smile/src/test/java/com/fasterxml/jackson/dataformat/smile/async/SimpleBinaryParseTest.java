package com.fasterxml.jackson.dataformat.smile.async;

import java.io.*;

import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;

public class SimpleBinaryParseTest extends AsyncTestBase
{
    private final SmileFactory F_RAW = new SmileFactory(); {
        F_RAW.disable(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT);
    }
    private final SmileFactory F_7BIT = new SmileFactory(); {
        F_7BIT.enable(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT);
    }

    final static int[] SIZES = new int[] {
        1, 2, 3, 4, 5, 7, 11,
        90, 350, 1900, 6000, 19000, 65000,
        139000
    };

    public void testRawAsRootValue() throws IOException {
        _testBinaryAsRoot(F_RAW);
    }

    public void testRawAsArray() throws IOException {
        _testBinaryAsArray(F_RAW);
    }

    public void testRawAsObject() throws IOException {
        _testBinaryAsObject(F_RAW);
    }

    public void test7BitAsArray() throws IOException {
        _testBinaryAsArray(F_7BIT);
    }

    public void test7BitAsObject() throws IOException {
        _testBinaryAsObject(F_7BIT);
    }

    public void test7BitAsRootValue() throws IOException {
        _testBinaryAsRoot(F_7BIT);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testBinaryAsRoot(SmileFactory f) throws IOException {
        _testBinaryAsRoot2(f, 1, Integer.MAX_VALUE);
        _testBinaryAsRoot2(f, 0, 3);
        _testBinaryAsRoot2(f, 1, 1);
    }

    private void _testBinaryAsObject(SmileFactory f) throws IOException {
        _testBinaryAsObject2(f, 1, Integer.MAX_VALUE);
        _testBinaryAsObject2(f, 0, 3);
        _testBinaryAsObject2(f, 1, 1);
    }

    private void _testBinaryAsArray(SmileFactory f) throws IOException {
        _testBinaryAsArray2(f, 1, Integer.MAX_VALUE);
        _testBinaryAsArray2(f, 0, 3);
        _testBinaryAsArray2(f, 1, 1);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testBinaryAsRoot2(SmileFactory f, int offset, int readSize) throws IOException
    {
        for (int size : SIZES) {
            byte[] binary = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);
            SmileGenerator g = f.createGenerator(bo);
            g.writeBinary(binary);
            g.close();
            byte[] smile = bo.toByteArray();

            // and verify
            AsyncReaderWrapper p = asyncForBytes(f, readSize, smile, offset);

            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertArrayEquals(binary, result);
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = asyncForBytes(f, readSize, smile, offset);
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }

    private void _testBinaryAsArray2(SmileFactory f, int offset, int readSize) throws IOException
    {
        for (int size : SIZES) {
            byte[] binary = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);
            SmileGenerator g = f.createGenerator(bo);
            g.writeStartArray();
            g.writeBinary(binary);
            g.writeNumber(1); // just to verify there's no overrun
            g.writeEndArray();
            g.close();
            byte[] smile = bo.toByteArray();

            // and verify
            AsyncReaderWrapper p = asyncForBytes(f, readSize, smile, offset);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());

            byte[] result = p.getBinaryValue();

            assertArrayEquals(binary, result);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = asyncForBytes(f, readSize, smile, offset);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }

    private void _testBinaryAsObject2(SmileFactory f, int offset, int readSize) throws IOException
    {
        for (int size : SIZES) {
            byte[] data = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);
            SmileGenerator g = f.createGenerator(bo);
            g.writeStartObject();
            g.writeFieldName("binary");
            g.writeBinary(data);
            g.writeEndObject();
            g.close();
            byte[] smile = bo.toByteArray();

            AsyncReaderWrapper p = asyncForBytes(f, readSize, smile, offset);
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("binary", p.currentName());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertArrayEquals(data, result);

            // also, via different accessor
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(result.length);
            assertEquals(result.length, p.parser().readBinaryValue(bytes));
            assertArrayEquals(data, bytes.toByteArray());

            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = asyncForBytes(f, readSize, smile, offset);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }

    private byte[] _generateData(int size)
    {
        byte[] result = new byte[size];
        for (int i = 0; i < size; ++i) {
            result[i] = (byte) (i % 255);
        }
        return result;
    }
}
