package com.fasterxml.jackson.dataformat.smile.async;

import java.io.*;

import static org.junit.Assert.*;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;

import tools.jackson.databind.ObjectWriter;

import com.fasterxml.jackson.dataformat.smile.SmileGenerator;

public class SimpleBinaryParseTest extends AsyncTestBase
{
    private final ObjectWriter W_RAW = _smileWriter(true)
            .without(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT);

    private final ObjectWriter W_7BIT = _smileWriter(true)
            .with(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT);

    final static int[] SIZES = new int[] {
        1, 2, 3, 4, 5, 7, 11,
        90, 350, 1900, 6000, 19000, 65000,
        139000
    };

    public void testRawAsRootValue() throws IOException {
        _testBinaryAsRoot(W_RAW);
    }

    public void testRawAsArray() throws IOException {
        _testBinaryAsArray(W_RAW);
    }

    public void testRawAsObject() throws IOException {
        _testBinaryAsObject(W_RAW);
    }
    
    public void test7BitAsArray() throws IOException {
        _testBinaryAsArray(W_7BIT);
    }

    public void test7BitAsObject() throws IOException {
        _testBinaryAsObject(W_7BIT);
    }

    public void test7BitAsRootValue() throws IOException {
        _testBinaryAsRoot(W_7BIT);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testBinaryAsRoot(ObjectWriter w) throws IOException {
        _testBinaryAsRoot2(w, 1, Integer.MAX_VALUE);
        _testBinaryAsRoot2(w, 0, 3);
        _testBinaryAsRoot2(w, 1, 1);
    }

    private void _testBinaryAsObject(ObjectWriter w) throws IOException {
        _testBinaryAsObject2(w, 1, Integer.MAX_VALUE);
        _testBinaryAsObject2(w, 0, 3);
        _testBinaryAsObject2(w, 1, 1);
    }

    private void _testBinaryAsArray(ObjectWriter w) throws IOException {
        _testBinaryAsArray2(w, 1, Integer.MAX_VALUE);
        _testBinaryAsArray2(w, 0, 3);
        _testBinaryAsArray2(w, 1, 1);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void _testBinaryAsRoot2(ObjectWriter w, int offset, int readSize) throws IOException
    {
        for (int size : SIZES) {
            byte[] binary = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);            
            JsonGenerator g = w.createGenerator(bo);
            g.writeBinary(binary);
            g.close();
            byte[] smile = bo.toByteArray();

            // and verify
            AsyncReaderWrapper p = asyncForBytes(_smileReader(), readSize, smile, offset);
            
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertArrayEquals(binary, result);
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = asyncForBytes(_smileReader(), readSize, smile, offset);
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }
    
    private void _testBinaryAsArray2(ObjectWriter w, int offset, int readSize) throws IOException
    {
        for (int size : SIZES) {
            byte[] binary = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);            
            JsonGenerator g = w.createGenerator(bo);
            g.writeStartArray();
            g.writeBinary(binary);
            g.writeNumber(1); // just to verify there's no overrun
            g.writeEndArray();
            g.close();
            byte[] smile = bo.toByteArray();            
            
            // and verify
            AsyncReaderWrapper p = asyncForBytes(_smileReader(), readSize, smile, offset);
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
            p = asyncForBytes(_smileReader(), readSize, smile, offset);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }
    
    private void _testBinaryAsObject2(ObjectWriter w, int offset, int readSize) throws IOException
    {
        for (int size : SIZES) {
            byte[] data = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);
            JsonGenerator g = w.createGenerator(bo);
            g.writeStartObject();
            g.writeName("binary");
            g.writeBinary(data);
            g.writeEndObject();
            g.close();
            byte[] smile = bo.toByteArray();
            
            AsyncReaderWrapper p = asyncForBytes(_smileReader(), readSize, smile, offset);
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
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
            p = asyncForBytes(_smileReader(), readSize, smile, offset);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
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
