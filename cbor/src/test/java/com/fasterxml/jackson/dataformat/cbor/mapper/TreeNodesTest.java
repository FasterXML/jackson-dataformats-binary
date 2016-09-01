package com.fasterxml.jackson.dataformat.cbor.mapper;

import org.junit.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

public class TreeNodesTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = new ObjectMapper(new CBORFactory());

    public void testSimple() throws Exception
    {
         // create the serialized JSON with byte array
         ObjectNode top1 = MAPPER.createObjectNode();
         ObjectNode foo1 = top1.putObject("foo");
         foo1.put("bar", "baz");
         final String TEXT =  "Caf\u00e9 1\u20ac";
         final byte[] TEXT_BYTES =  TEXT.getBytes("UTF-8");
         foo1.put("dat", TEXT_BYTES);

         byte[] doc = MAPPER.writeValueAsBytes(top1);
         // now, deserialize
         JsonNode top2 = MAPPER.readValue(doc, JsonNode.class);
         JsonNode foo2 = top2.get("foo");
         assertEquals("baz", foo2.get("bar").textValue());
    
         JsonNode datNode = foo2.get("dat");
         if (!datNode.isBinary()) {
             fail("Expected binary node; got "+datNode.getClass().getName());
         }
         byte[] bytes = datNode.binaryValue();
         Assert.assertArrayEquals(TEXT_BYTES, bytes);
     }

    public void testNumbers() throws Exception
    {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("value", 0.25f);

        byte[] doc = MAPPER.writeValueAsBytes(root);
        // now, deserialize
        JsonNode result = MAPPER.readValue(doc, JsonNode.class);
        assertEquals(1, result.size());

        // and verify we get FloatNode
        JsonNode valueNode = result.get("value");
        assertNotNull(valueNode);
        assertTrue(valueNode.isNumber());
        assertTrue(valueNode.isFloatingPointNumber());
        assertTrue(valueNode.isFloat());
    }
}
