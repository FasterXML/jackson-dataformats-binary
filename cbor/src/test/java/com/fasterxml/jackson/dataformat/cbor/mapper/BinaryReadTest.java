package com.fasterxml.jackson.dataformat.cbor.mapper;

import java.io.*;

import org.junit.Assert;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.util.ThrottledInputStream;

public class BinaryReadTest extends CBORTestBase
{
    final static class Bytes {
        public byte[] bytes;
        
        public Bytes() { }
        public Bytes(byte[] b) { bytes = b; }
    }

    @JsonPropertyOrder({ "bytes1", "bytes2", "bytes3" })
    final static class Bytes3 {
        public byte[] bytes1, bytes2, bytes3;

        public Bytes3() { }
        public Bytes3(byte[] b) {
            bytes1 = b;
            bytes2 = b;
            bytes3 = b;
        }
    }

    private final ObjectMapper MAPPER = cborMapper();

    public void testSmallBinaryValues() throws Exception {
        _testBinary(0);
        _testBinary(1);
        _testBinary(20);
        _testBinary(100);
    }

    public void testMediumBinaryValues() throws Exception {
        _testBinary(500);
        _testBinary(1500);
        _testBinary(8900);
    }

    public void testLargeBinaryValues() throws Exception {
        _testBinary(99000);
        _testBinary(299000);
        _testBinary(740000);
    }

    // And then one test just to ensure no state corruption occurs
    public void testMultipleBinaryFields() throws Exception
    {
        byte[] inputBytes = new byte[900];
        for (int i = 0; i < inputBytes.length; ++i) {
            inputBytes[i] = (byte) i;
        }
        Bytes3 input = new Bytes3(inputBytes);
        byte[] raw = MAPPER.writeValueAsBytes(input);

        Bytes3 result = MAPPER.readValue(raw, Bytes3.class);
        Assert.assertArrayEquals(input.bytes1, result.bytes1);
        Assert.assertArrayEquals(input.bytes2, result.bytes2);
        Assert.assertArrayEquals(input.bytes3, result.bytes3);
    }
    
    public void _testBinary(int size) throws Exception
    {
        byte[] input = new byte[size];
        for (int i = 0; i < input.length; ++i) {
            input[i] = (byte) i;
        }
        
        // First, read/write as individual value
        byte[] raw = MAPPER.writeValueAsBytes(input);
        byte[] b2 = MAPPER.readValue(raw, byte[].class);
        assertNotNull(b2);
        Assert.assertArrayEquals(input, b2);
        
        // then as POJO member
        raw = MAPPER.writeValueAsBytes(new Bytes(input));
        Bytes bytes = MAPPER.readValue(raw, Bytes.class);
        assertNotNull(bytes);
        assertNotNull(bytes.bytes);
        Assert.assertArrayEquals(input, bytes.bytes);

        // then using incremental access method
        raw = MAPPER.writeValueAsBytes(input);

        InputStream in = new ThrottledInputStream(raw, 3);
        JsonParser p = MAPPER.getFactory().createParser(in);
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        ByteArrayOutputStream bout = new ByteArrayOutputStream(input.length / 3);
        assertEquals(input.length, p.readBinaryValue(bout));
        assertEquals(input.length, bout.size());
        b2 = bout.toByteArray();
        Assert.assertArrayEquals(input, b2);
        assertNull(p.nextToken());
        p.close();
        in.close();

        // and finally streaming but skipping
        in = new ThrottledInputStream(raw, 3);
        p = MAPPER.getFactory().createParser(in);
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
        in.close();
    }
}
