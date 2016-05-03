package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import org.junit.Assert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ParserBinaryTest extends CBORTestBase
{
    final static class Bytes {
        public byte[] bytes;
        
        public Bytes() { }
        public Bytes(byte[] b) { bytes = b; }
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

        // and finally using incremental access method
        raw = MAPPER.writeValueAsBytes(input);
        JsonParser p = MAPPER.getFactory().createParser(raw);
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        ByteArrayOutputStream bout = new ByteArrayOutputStream(input.length / 3);
        assertEquals(input.length, p.readBinaryValue(bout));
        assertEquals(input.length, bout.size());
        b2 = bout.toByteArray();
        Assert.assertArrayEquals(input, b2);
        p.close();
    }
}
