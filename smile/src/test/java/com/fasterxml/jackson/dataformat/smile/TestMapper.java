package com.fasterxml.jackson.dataformat.smile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestMapper extends SmileTestBase
{
    static class BytesBean {
        public byte[] bytes;
        
        public BytesBean() { }
        public BytesBean(byte[] b) { bytes = b; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */
    
    private final ObjectMapper MAPPER = smileMapper();
    
    // [JACKSON-733]
    public void testBinary() throws IOException
    {
        byte[] input = new byte[] { 1, 2, 3, -1, 8, 0, 42 };
        byte[] smile = MAPPER.writeValueAsBytes(new BytesBean(input));
        BytesBean result = MAPPER.readValue(smile, BytesBean.class);
        
        assertNotNull(result.bytes);
        Assert.assertArrayEquals(input, result.bytes);
    }

    // @since 2.1
    public void testCopy() throws IOException
    {
        ObjectMapper mapper1 = smileMapper();
        ObjectMapper mapper2 = mapper1.copy();
        
        assertNotSame(mapper1, mapper2);
        assertNotSame(mapper1.getFactory(), mapper2.getFactory());
        assertEquals(SmileFactory.class, mapper2.getFactory().getClass());
    }

    // UUIDs should be written as binary (starting with 2.3)
    public void testUUIDs() throws IOException
    {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = MAPPER.writeValueAsBytes(uuid);

        // first, just verify we can decode it
        UUID out = MAPPER.readValue(bytes, UUID.class);
        assertEquals(uuid, out);

        // then verify it comes back as binary
        JsonParser p = MAPPER.getFactory().createParser(bytes);
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        byte[] b = p.getBinaryValue();
        assertNotNull(b);
        assertEquals(16, b.length);
        p.close();
    }

    public void testWithNestedMaps() throws IOException
    {
        Map<Object,Object> map = new HashMap<Object,Object>();
        map.put("foo", Collections.singletonMap("", "bar"));
        byte[] bytes = MAPPER.writeValueAsBytes(map);
        JsonNode n = MAPPER.readTree(new ByteArrayInputStream(bytes));
        assertTrue(n.isObject());
        assertEquals(1, n.size());
        JsonNode n2 = n.get("foo");
        assertNotNull(n2);
        assertEquals(1, n2.size());
        JsonNode n3 = n2.get("");
        assertNotNull(n3);
        assertTrue(n3.isTextual());
    }
}

