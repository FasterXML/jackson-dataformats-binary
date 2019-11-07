package com.fasterxml.jackson.dataformat.cbor.failing;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

// Tests for [dataformat-binary#188], lacking coercions for Binary->String wrt containers
public class BinaryToStringCoercionTest extends CBORTestBase
{
    static class BinaryListWrapper {
        public List<byte[]> data = Collections.singletonList(
                new byte[] { 1, 2, 3, 4});
    }

    static class StringListWrapper {
        public List<String> data;
    }
    
    static class BinarySetWrapper {
        public Set<byte[]> data = Collections.singleton(new byte[] { 1, 2, 3, 4});
    }

    static class StringSetWrapper {
        public Set<String> data;
    }
    
    static class BinaryArrayWrapper {
        public byte[][] data =  new byte[][] { new byte[] { 1, 2, 3, 4} };
    }

    static class StringArrayWrapper {
        public String[] data;
    }

    static class BinaryMapWrapper {
        public Map<String, byte[]> data = Collections.singletonMap("key",
                new byte[] { 1, 2, 3, 4});
    }

    static class StringMapWrapper {
        public Map<String, String> data;
    }
    
    private final ObjectMapper CBOR_MAPPER = cborMapper();

    public void testWithList() throws Exception
    {
        byte[] doc = CBOR_MAPPER.writeValueAsBytes(new BinaryListWrapper());
        StringListWrapper result = CBOR_MAPPER.readValue(doc, StringListWrapper.class);
        assertEquals(1, result.data.size());
        assertEquals(String.class, result.data.get(0).getClass());
    }

    public void testWithSet() throws Exception
    {
        byte[] doc = CBOR_MAPPER.writeValueAsBytes(new BinarySetWrapper());
        StringSetWrapper result = CBOR_MAPPER.readValue(doc, StringSetWrapper.class);
        assertEquals(1, result.data.size());
        assertEquals(String.class, result.data.iterator().next().getClass());
    }

    public void testWithMap() throws Exception
    {
        byte[] doc = CBOR_MAPPER.writeValueAsBytes(new BinaryMapWrapper());
        StringMapWrapper result = CBOR_MAPPER.readValue(doc, StringMapWrapper.class);
        assertEquals(1, result.data.size());
        assertEquals(String.class, result.data.get("key").getClass());
    }

    public void testWithArray() throws Exception
    {
        byte[] doc = CBOR_MAPPER.writeValueAsBytes(new BinaryArrayWrapper());
        StringArrayWrapper result = CBOR_MAPPER.readValue(doc, StringArrayWrapper.class);
        assertEquals(1, result.data.length);
        assertEquals(String.class, result.data[0].getClass());
    }
}

