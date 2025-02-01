package tools.jackson.dataformat.protobuf;

import java.io.StringReader;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadPackedRepeatedTest extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    @Test
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

        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }

    // 31-Jan-2025, tatu: [dataformats-binary#561] Problem with sparse arrays
    //   and trailing END_OBJECT
    @Test
    public void testSparse561() throws Exception
    {
        final String SCHEMA_STR =
            "package mypackage;\n"
            + "message t {\n"
            + "        repeated uint32 f = 1;\n"
            + "}";

        Map<String, Object> input = Map.of("f", new int[] { 100, 200 });

        ProtobufSchema schema = MAPPER.schemaLoader().load(new StringReader(SCHEMA_STR));
        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(input);
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(encoded);

        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }
}
