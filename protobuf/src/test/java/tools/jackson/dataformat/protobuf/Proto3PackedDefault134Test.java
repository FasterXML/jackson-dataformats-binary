package tools.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#134]: proto3 defaults repeated scalar/enum fields to
// "packed" wire encoding unless explicitly overridden; this library only
// ever honored an explicit `[packed=...]` option, causing decode failures
// for proto3-encoded content produced by real protoc-based implementations.
public class Proto3PackedDefault134Test extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    static class IntArray {
        public int[] f;

        protected IntArray() { }
        public IntArray(int... v) { f = v; }
    }

    @Test
    public void testProto3RepeatedScalarDefaultsToPacked() throws Exception
    {
        final String SCHEMA_STR = "syntax = \"proto3\";\n"
                + "message t {\n"
                + "  repeated uint32 f = 1;\n"
                + "}\n";
        // As produced by a real proto3 encoder: packed (length-delimited) encoding
        final byte[] pb = { 0xa, 0x3, 0x64, (byte) 0xc8, 0x1 }; // f = [100, 200], packed

        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(pb);

        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }

    @Test
    public void testProto3ExplicitUnpackedOverrideStillHonored() throws Exception
    {
        final String SCHEMA_STR = "syntax = \"proto3\";\n"
                + "message t {\n"
                + "  repeated uint32 f = 1 [packed=false];\n"
                + "}\n";
        // unpacked: each value gets its own tag
        final byte[] pb = { 0x8, 0x64, 0x8, (byte) 0xc8, 0x1 }; // f = [100, 200], unpacked

        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(pb);

        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }

    @Test
    public void testProto2RepeatedScalarStaysUnpackedByDefault() throws Exception
    {
        final String SCHEMA_STR = "syntax = \"proto2\";\n"
                + "message t {\n"
                + "  repeated uint32 f = 1;\n"
                + "}\n";
        // unpacked: proto2 default, unaffected by this fix
        final byte[] pb = { 0x8, 0x64, 0x8, (byte) 0xc8, 0x1 }; // f = [100, 200], unpacked

        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(pb);

        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }

    @Test
    public void testNoSyntaxDeclarationStaysUnpackedByDefault() throws Exception
    {
        // no `syntax = ...;` line at all: must behave exactly as before (unpacked default)
        final String SCHEMA_STR = "message t {\n"
                + "  repeated uint32 f = 1;\n"
                + "}\n";
        final byte[] pb = { 0x8, 0x64, 0x8, (byte) 0xc8, 0x1 }; // f = [100, 200], unpacked

        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(pb);

        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }

    @Test
    public void testProto3WriteDefaultsToPacked() throws Exception
    {
        final String SCHEMA_STR = "syntax = \"proto3\";\n"
                + "message t {\n"
                + "  repeated uint32 f = 1;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR);
        ObjectWriter w = MAPPER.writer(schema);
        byte[] bytes = w.writeValueAsBytes(new IntArray(100, 200));

        // packed: 1 byte tag, 1 byte length, 3 bytes for 100 (0x64) + 200 (0xc8,0x01) = 5
        assertEquals(5, bytes.length);
        assertEquals(0xa, bytes[0]); // field 1, length-prefixed wire type

        // round-trip through the same schema
        JsonNode t = MAPPER.readerFor(JsonNode.class).with(schema).readValue(bytes);
        assertEquals(2, t.get("f").size());
        assertEquals(100, t.get("f").get(0).asInt());
        assertEquals(200, t.get("f").get(1).asInt());
    }
}
