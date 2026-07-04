package com.fasterxml.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// [dataformats-binary#134]: fields declared inside a `oneof` block live in a
// separate list (`MessageElement.oneOfs()`) from regular fields
// (`MessageElement.fields()`); TypeResolver only ever resolved the latter,
// so `oneof` member fields were silently dropped from the schema -- no
// error, they simply couldn't be read or written.
public class OneofFieldResolutionTest extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    private final static String SCHEMA_STR = "message t {\n"
            + "  oneof choice {\n"
            + "    string a = 1;\n"
            + "    int32 b = 2;\n"
            + "  }\n"
            + "  optional string other = 3;\n"
            + "}\n";

    @Test
    public void testOneofFieldsAreResolved() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);

        assertEquals(3, schema.getRootType().getFieldCount());
        assertNotNull(schema.getRootType().field("a"));
        assertNotNull(schema.getRootType().field("b"));
        assertNotNull(schema.getRootType().field("other"));
    }

    @Test
    public void testOneofFieldsRoundTrip() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("a", "value-for-a");
        input.put("other", "other-value");

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        JsonNode result = MAPPER.readerFor(JsonNode.class).with(schema).readValue(bytes);

        assertEquals("value-for-a", result.get("a").asText());
        assertEquals("other-value", result.get("other").asText());
    }
}
