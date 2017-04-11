package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

import java.io.StringReader;
import java.util.Base64;

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
        final String base64pb = "CgNkyAE=";         // f = [100, 200]
        final byte[] pb = Base64.getDecoder().decode(base64pb);

        ProtobufSchema schema = MAPPER.schemaLoader().load(new StringReader(SCHEMA_STR));
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(pb);

        String s = t.get("f").asText();
        assertEquals(t.get("f").size(), 2);
        assertEquals(t.get("f").get(0).asInt(), 100);
        assertEquals(t.get("f").get(1).asInt(), 200);
    }
}
