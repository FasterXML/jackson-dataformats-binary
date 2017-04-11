package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

import java.io.StringReader;

public class ReadPackedRepeatedTest extends ProtobufTestBase
{
    final ProtobufMapper MAPPER = new ProtobufMapper();

    public void testPacked() throws Exception
    {
        final String SCHEMA_STR =
            "package mypackage;\n"
            + "message t {\n"
            + "        repeated uint32 f = 1 [packed=true];\n"
            + "}";
        final byte[] pb = {0xa, 0x3, 0x64, (byte)0xc8, 0x1}; // f = [100, 200]

        ProtobufSchema schema = MAPPER.schemaLoader().load(new StringReader(SCHEMA_STR));
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(pb);

        String s = t.get("f").asText();
        assertEquals(t.get("f").size(), 2);
        assertEquals(t.get("f").get(0).asInt(), 100);
        assertEquals(t.get("f").get(1).asInt(), 200);
    }
}
