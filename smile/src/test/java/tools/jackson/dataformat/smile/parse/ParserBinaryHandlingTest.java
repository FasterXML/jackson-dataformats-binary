package tools.jackson.dataformat.smile.parse;

import java.io.*;

import static org.junit.Assert.*;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileGenerator;
import tools.jackson.dataformat.smile.SmileParser;

public class ParserBinaryHandlingTest extends BaseTestForSmile
{
    final static int[] SIZES = new int[] {
        1, 2, 3, 4, 5, 6,
        7, 8, 12,
        100, 350, 1900, 6000, 19000, 65000,
        139000
    };
    
    public void testRawAsArray() throws IOException
    {
        _testBinaryAsArray(true);
    }

    public void test7BitAsArray() throws IOException
    {
        _testBinaryAsArray(false);
    }

    // Added based on [JACKSON-376]
    public void testRawAsObject() throws IOException
    {
        _testBinaryAsObject(true);
    }

    // Added based on [JACKSON-376]
    public void test7BitAsObject() throws IOException
    {
        _testBinaryAsObject(false);
    }

    public void testRawAsRootValue() throws IOException {
        _testBinaryAsRoot(true);
    }

    public void test7BitAsRootValue() throws IOException {
        _testBinaryAsRoot(false);
    }

    public void testStreamingRaw() throws IOException {
        _testStreaming(true);
    }

    public void testStreamingEncoded() throws IOException {
        _testStreaming(false);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testBinaryAsRoot(boolean raw) throws IOException
    {
        SmileFactory f = SmileFactory.builder()
                .configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, !raw)
                .build();
        for (int size : SIZES) {
            byte[] data = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);            
            SmileGenerator g = (SmileGenerator) f.createGenerator(ObjectWriteContext.empty(), bo);
            g.writeBinary(data);
            g.close();
            byte[] smile = bo.toByteArray();            
            
            // and verify
            SmileParser p = (SmileParser) _smileParser(smile);
            assertEquals(raw, p.mayContainRawBinary());
            
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertArrayEquals(data, result);
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = (SmileParser) _smileParser(smile);
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }

    private void _testBinaryAsArray(boolean raw) throws IOException
    {
        SmileFactory f = SmileFactory.builder()
            .configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, !raw)
            .build();
        for (int size : SIZES) {
            byte[] data = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);            
            JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), bo);
            g.writeStartArray();
            g.writeBinary(data);
            g.writeNumber(1); // just to verify there's no overrun
            g.writeEndArray();
            g.close();
            byte[] smile = bo.toByteArray();            
            
            // and verify
            JsonParser p = _smileParser(smile);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertArrayEquals(data, result);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = _smileParser(smile);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }

    private void _testBinaryAsObject(boolean raw) throws IOException
    {
        SmileFactory f = SmileFactory.builder()
                .configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, !raw)
                .build();
        for (int size : SIZES) {
            byte[] data = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);            
            JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), bo);
            g.writeStartObject();
            g.writeName("binary");
            g.writeBinary(data);
            g.writeEndObject();
            g.close();
            byte[] smile = bo.toByteArray();            
            
            // and verify
            JsonParser p = _smileParser(smile);
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("binary", p.currentName());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertArrayEquals(data, result);
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();

            // and second time around, skipping
            p = _smileParser(smile);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
            p.close();
        }
    }

    private void _testStreaming(boolean raw) throws IOException
    {
        SmileFactory f = SmileFactory.builder()
                .configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, !raw)
                .build();
        for (int size : SIZES) {
            byte[] data = _generateData(size);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size+10);            
            JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), bo);
            g.writeStartObject();
            g.writeName("b");
            g.writeBinary(data);
            g.writeEndObject();
            g.close();
            byte[] smile = bo.toByteArray();            
            
            // and verify
            JsonParser p = _smileParser(smile);
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("b", p.currentName());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            ByteArrayOutputStream result = new ByteArrayOutputStream(size);
            int gotten = p.readBinaryValue(result);
            assertEquals(size, gotten);
            assertArrayEquals(data, result.toByteArray());
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
