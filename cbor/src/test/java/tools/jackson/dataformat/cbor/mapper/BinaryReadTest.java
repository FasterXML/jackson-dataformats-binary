package tools.jackson.dataformat.cbor.mapper;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.BinaryNode;
import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.testutil.ThrottledInputStream;

public class BinaryReadTest extends CBORTestBase
{
    final static class Bytes {
        public byte[] bytes;
        
        public Bytes() { }
        public Bytes(byte[] b) { bytes = b; }
    }

    final static class ByteArrays {
        public List<byte[]> arrays;

        public ByteArrays() { }
        public ByteArrays(byte[]... b) { arrays = Arrays.asList(b); }
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
        byte[] input = _bytes(size, 0);
        
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
        JsonParser p = MAPPER.createParser(in);
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
        p = MAPPER.createParser(in);
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
        in.close();

        // And once more! Read as tree
        JsonNode n = MAPPER.readTree(MAPPER.writeValueAsBytes(new Bytes(input)));
        assertTrue(n.isObject());
        assertEquals(1, n.size());

        n = n.get("bytes");
        assertNotNull(n);
        assertTrue(n.isBinary());
        BinaryNode bn = (BinaryNode) n;
        Assert.assertArrayEquals(input, bn.binaryValue());

        _testBinaryInArray(size);
    }

    private void _testBinaryInArray(int size) throws Exception
    {
        byte[] b1 = _bytes(size, 1);
        byte[] b2 = _bytes(size, 7);
        byte[] doc = MAPPER.writeValueAsBytes(new ByteArrays(b1, b2));
        @SuppressWarnings("resource")
        ByteArrays result = MAPPER.readValue(new ThrottledInputStream(doc, 5),
                ByteArrays.class);
        assertNotNull(result.arrays);
        assertEquals(2, result.arrays.size());
        Assert.assertArrayEquals(b1, result.arrays.get(0));
        Assert.assertArrayEquals(b2, result.arrays.get(1));

        // and once more, now as JsonNode
        JsonNode n = MAPPER.readTree(doc);
        assertTrue(n.isObject());
        JsonNode n2 = n.get("arrays");
        assertTrue(n2.isArray());
        assertEquals(2, n2.size());

        JsonNode bin = n2.get(0);
        assertTrue(bin.isBinary());
        Assert.assertArrayEquals(b1, ((BinaryNode) bin).binaryValue());

        bin = n2.get(1);
        assertTrue(bin.isBinary());
        Assert.assertArrayEquals(b2, ((BinaryNode) bin).binaryValue());
    }

    private byte[] _bytes(int size, int offset) {
        byte[] input = new byte[size];
        for (int i = 0; i < input.length; ++i) {
            input[i] = (byte) (i + offset);
        }
        return input;
    }
}
