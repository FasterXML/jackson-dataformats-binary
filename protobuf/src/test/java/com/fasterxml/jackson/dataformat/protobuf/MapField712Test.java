package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufField;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for idiomatic {@code map<K,V>} support: [dataformats-binary#712].
 */
public class MapField712Test extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    // POJOs for databind round-trips
    static class Counts {
        public Map<String, Integer> counts;
        public String name;
    }
    static class Val {
        public int x;
        public String y;
        protected Val() { }
        public Val(int x, String y) { this.x = x; this.y = y; }
    }
    static class MsgMap {
        public Map<Long, Val> m;
    }

    /*
    /**********************************************************
    /* Schema resolution
    /**********************************************************
     */

    @Test
    public void testMapSchemaResolution() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  map<string, int32> counts = 1;\n"
                + "  string name = 2;\n"
                + "}\n", "Msg");
        ProtobufField f = schema.getRootType().field("counts");
        assertNotNull(f);
        assertTrue(f.isMap);
        assertTrue(f.repeated); // encoded as repeated entries
        assertNotNull(f.getKeyField());
        assertNotNull(f.getValueField());
    }

    @Test
    public void testInvalidMapKeyTypeRejected() throws Exception
    {
        for (String badKey : new String[] { "double", "float", "bytes" }) {
            final String proto = "syntax = \"proto3\";\n"
                    + "message Msg {\n"
                    + "  map<" + badKey + ", int32> m = 1;\n"
                    + "}\n";
            try {
                ProtobufSchemaLoader.std.parse(proto, "Msg");
                fail("Should not accept `map` with key type `" + badKey + "`");
            } catch (IllegalArgumentException e) {
                verifyException(e, "map keys must be");
            }
        }
    }

    /*
    /**********************************************************
    /* Streaming round-trips
    /**********************************************************
     */

    @Test
    public void testStringToIntMapTokens() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  map<string, int32> counts = 1;\n"
                + "  string name = 2;\n"
                + "}\n", "Msg");
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("a", 1);
        counts.put("b", 2);
        root.put("counts", counts);
        root.put("name", "x");
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);

        try (JsonParser p = MAPPER.getFactory().createParser(doc)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("counts", p.currentName());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("a", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("b", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(2, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("name", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("x", p.getText());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertEquals(null, p.nextToken());
        }
    }

    @Test
    public void testMapDatabindRoundtrip() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Counts {\n"
                + "  map<string, int32> counts = 1;\n"
                + "  string name = 2;\n"
                + "}\n", "Counts");
        Counts input = new Counts();
        input.counts = new LinkedHashMap<>();
        input.counts.put("a", 1);
        input.counts.put("bb", 22);
        input.name = "hi";

        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(input);
        Counts result = MAPPER.readerFor(Counts.class).with(schema).readValue(doc);
        assertEquals(input.counts, result.counts);
        assertEquals("hi", result.name);
    }

    @Test
    public void testNonStringKeyMaps() throws Exception
    {
        // int32, int64, bool keys
        _verifyKeyRoundtrip("int32", "int32", "7", 7);
        _verifyKeyRoundtrip("int64", "int64", "100000000000", 100000000000L);
        _verifyKeyRoundtrip("sint32", "int32", "-5", -5);
        _verifyKeyRoundtrip("bool", "int32", "true", 1);
    }

    private void _verifyKeyRoundtrip(String keyType, String valType,
            String key, Object expectedNumKey) throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  map<" + keyType + ", " + valType + "> m = 1;\n"
                + "}\n", "Msg");
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, 42);
        root.put("m", m);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);

        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(1, tree.get("m").size());
        assertEquals(42, tree.get("m").get(key).asInt());
    }

    @Test
    public void testMessageValuedMap() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Val { int32 x = 1; string y = 2; }\n"
                + "message MsgMap { map<int64, Val> m = 1; }\n", "MsgMap");
        MsgMap input = new MsgMap();
        input.m = new LinkedHashMap<>();
        input.m.put(1L, new Val(10, "one"));
        input.m.put(2L, new Val(20, "two"));
        input.m.put(3L, new Val(30, "three"));

        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(input);
        MsgMap result = MAPPER.readerFor(MsgMap.class).with(schema).readValue(doc);
        assertEquals(3, result.m.size());
        assertEquals(20, result.m.get(2L).x);
        assertEquals("three", result.m.get(3L).y);
    }

    @Test
    public void testEmptyMapWritesNothing() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  map<string, int32> counts = 1;\n"
                + "  string name = 2;\n"
                + "}\n", "Msg");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("counts", new LinkedHashMap<>());
        root.put("name", "y");
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);

        // Empty map => field entirely absent on the wire; only "name" (tag 2, len 1, 'y')
        assertArrayEquals(new byte[] { 0x12, 0x01, 0x79 }, doc);

        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertTrue(tree.path("counts").isMissingNode() || tree.path("counts").size() == 0);
        assertEquals("y", tree.get("name").asText());
    }

    @Test
    public void testMapNestedInRepeatedMessage() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Inner { map<string, int32> m = 1; }\n"
                + "message Outer { repeated Inner items = 1; }\n", "Outer");
        Map<String, Object> root = new LinkedHashMap<>();
        java.util.List<Object> items = new java.util.ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            Map<String, Object> inner = new LinkedHashMap<>();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k" + i, i);
            inner.put("m", m);
            items.add(inner);
        }
        root.put("items", items);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);

        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(3, tree.get("items").size());
        assertEquals(2, tree.get("items").get(2).get("m").get("k2").asInt());
    }

    @Test
    public void testProto2Map() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto2\";\n"
                + "message Msg {\n"
                + "  map<string, int32> counts = 1;\n"
                + "}\n", "Msg");
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("a", 1);
        root.put("counts", counts);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);

        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(1, tree.get("counts").get("a").asInt());
    }

    /*
    /**********************************************************
    /* Wire-format and interoperability checks
    /**********************************************************
     */

    @Test
    public void testKnownWireEncoding() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; }\n", "Msg");
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> counts = new TreeMap<>();
        counts.put("a", 1);
        counts.put("b", 2);
        root.put("counts", counts);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);

        // Two entries; each: tag 1 (0x0a) | len 5 | key(0a 01 <c>) | value(10 <v>)
        byte[] expected = {
                0x0a, 0x05, 0x0a, 0x01, 0x61, 0x10, 0x01, // {"a":1}
                0x0a, 0x05, 0x0a, 0x01, 0x62, 0x10, 0x02  // {"b":2}
        };
        assertArrayEquals(expected, doc);
    }

    // A value written before the key (or an absent key) must decode using proto3
    // defaults, matching what other protobuf libraries can emit.
    @Test
    public void testAbsentKeyAndValueDefaults() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; }\n", "Msg");

        // Entry with only key present (value absent -> default 0): 0a 03 [0a 01 6b]
        JsonNode t1 = MAPPER.readerFor(JsonNode.class).with(schema)
                .readValue(new byte[] { 0x0a, 0x03, 0x0a, 0x01, 0x6b });
        assertEquals(0, t1.get("counts").get("k").asInt());

        // Entry with only value present (key absent -> default ""): 0a 02 [10 05]
        JsonNode t2 = MAPPER.readerFor(JsonNode.class).with(schema)
                .readValue(new byte[] { 0x0a, 0x02, 0x10, 0x05 });
        assertEquals(5, t2.get("counts").get("").asInt());
    }

    @Test
    public void testUnknownFieldInsideEntrySkipped() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; }\n", "Msg");
        // Entry {key="k", value=1} with an extra unknown field id 3 (varint 9) interleaved:
        //   key: 0a 01 6b | unknown(id3,varint): 18 09 | value: 10 01   -> body len 7
        byte[] doc = { 0x0a, 0x07, 0x0a, 0x01, 0x6b, 0x18, 0x09, 0x10, 0x01 };
        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(1, tree.get("counts").get("k").asInt());
    }

    // Large map read from an InputStream, forcing buffer reloads mid-map
    @Test
    public void testLargeMapAcrossBufferReload() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Big { map<int32, string> m = 1; }\n", "Big");
        final int COUNT = 2000;
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> big = new LinkedHashMap<>();
        for (int i = 0; i < COUNT; ++i) {
            big.put(String.valueOf(i), "value-string-number-" + i);
        }
        root.put("m", big);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);
        assertTrue(doc.length > 8000, "doc should exceed one input buffer");

        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema)
                .readValue(new ByteArrayInputStream(doc));
        assertEquals(COUNT, tree.get("m").size());
        for (int i = 0; i < COUNT; ++i) {
            assertEquals("value-string-number-" + i,
                    tree.get("m").get(String.valueOf(i)).asText());
        }
    }

    // Content trailing the value inside an entry must be consumed as part of the entry,
    // not leak out into the enclosing message.
    @Test
    public void testUnknownFieldAfterScalarValueInEntry() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; string name = 2; }\n", "Msg");
        // entry body: key(0a 01 6b) value(10 01) unknown(id 3, varint: 18 09) = 7 bytes,
        // then a genuine `name` field of the enclosing message
        byte[] doc = { 0x0a, 0x07, 0x0a, 0x01, 0x6b, 0x10, 0x01, 0x18, 0x09,
                       0x12, 0x01, 0x7a };
        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(1, tree.get("counts").get("k").asInt());
        assertEquals("z", tree.get("name").asText());
    }

    // Same, but for a message-valued entry: the stray field used to be decoded as a
    // field of the entry message, yielding an extra END_OBJECT.
    @Test
    public void testUnknownFieldAfterMessageValueInEntry() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Val { int32 x = 1; }\n"
                + "message Msg { map<string, Val> m = 1; string name = 2; }\n", "Msg");
        // entry body: key(0a 01 6b) value(12 02 08 07) unknown(18 09) = 9 bytes
        byte[] doc = { 0x0a, 0x09, 0x0a, 0x01, 0x6b, 0x12, 0x02, 0x08, 0x07, 0x18, 0x09,
                       0x12, 0x01, 0x7a };

        // Token stream must match the well-formed encoding exactly (no stray END_OBJECT)
        byte[] clean = { 0x0a, 0x07, 0x0a, 0x01, 0x6b, 0x12, 0x02, 0x08, 0x07,
                         0x12, 0x01, 0x7a };
        assertEquals(_tokens(schema, clean), _tokens(schema, doc));

        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(7, tree.get("m").get("k").get("x").asInt());
        assertEquals("z", tree.get("name").asText());
    }

    private String _tokens(ProtobufSchema schema, byte[] doc) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (JsonParser p = MAPPER.getFactory().createParser(doc)) {
            p.setSchema(schema);
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                sb.append(t).append(' ');
            }
        }
        return sb.toString();
    }

    // Protobuf does not constrain field order, so an entry may encode `value` (tag 2)
    // before `key` (tag 1). A forward-only parser can not surface that key, so it must
    // be reported -- silently yielding the default key would be wrong data.
    @Test
    public void testKeyAfterValueInEntryReported() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; }\n", "Msg");
        // entry body: value(10 05) key(0a 01 6b) = 5 bytes
        byte[] doc = { 0x0a, 0x05, 0x10, 0x05, 0x0a, 0x01, 0x6b };
        try {
            MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
            fail("Should not pass: 'key' follows 'value' within entry");
        } catch (Exception e) {
            verifyException(e, "'key' (id 1) follows 'value'");
            verifyException(e, "counts");
        }
    }

    // Two `key` fields within one entry: distinct from the above, and must say so
    @Test
    public void testDuplicateKeyInEntryReported() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; }\n", "Msg");
        // entry body: key(0a 01 6b) key(0a 01 63) = 6 bytes
        byte[] doc = { 0x0a, 0x06, 0x0a, 0x01, 0x6b, 0x0a, 0x01, 0x63 };
        try {
            MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
            fail("Should not pass: duplicate 'key' within entry");
        } catch (Exception e) {
            verifyException(e, "duplicate 'key' (id 1)");
            verifyException(e, "counts");
        }
    }

    // The entry-end bound must survive a buffer reload that happens while scanning an
    // entry's fields, not just while reading the value: end offsets are rebased on
    // reload (see `ProtobufReadContext.adjustEnd()`), so a bound captured beforehand
    // goes stale and lets the scan run past the entry into the next one.
    @Test
    public void testUnknownFieldInEntryAcrossBufferReload() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; string name = 2; }\n", "Msg");
        // Entry carrying only an unknown field (id 3, varint): key and value are both
        // absent, so both decode to proto3 defaults.
        final byte[] entryUnknownOnly = { 0x0a, 0x02, 0x18, 0x09 };
        final byte[] entryA = { 0x0a, 0x05, 0x0a, 0x01, 0x61, 0x10, 0x01 }; // {"a":1}

        // Sweep a padding prefix so the entry straddles the read-buffer boundary
        // (8000 bytes, jackson-core's default) at every alignment.
        for (int padLen = 7980; padLen <= 8020; ++padLen) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            b.write(0x12); // tag 2 (`name`), length-prefixed
            b.write(0x80 | (padLen & 0x7F)); // 2-byte varint length
            b.write(padLen >> 7);
            for (int i = 0; i < padLen; ++i) {
                b.write('x');
            }
            b.write(entryUnknownOnly);
            b.write(entryA);
            final byte[] doc = b.toByteArray();

            JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema)
                    .readValue(new ByteArrayInputStream(doc));
            JsonNode counts = tree.get("counts");
            final String desc = "padLen "+padLen;
            assertEquals(2, counts.size(), desc);
            assertEquals(0, counts.get("").asInt(), desc);
            assertEquals(1, counts.get("a").asInt(), desc);
        }
    }

    @Test
    public void testWriteStartArrayOnMapRejected() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message Msg { map<string, int32> counts = 1; }\n", "Msg");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (com.fasterxml.jackson.core.JsonGenerator g = MAPPER.getFactory().createGenerator(bytes)) {
            g.setSchema(schema);
            g.writeStartObject();
            g.writeFieldName("counts");
            try {
                g.writeStartArray();
                fail("Should not allow START_ARRAY for a map field");
            } catch (Exception e) {
                verifyException(e, "is a `map`");
            }
        }
    }
}
