package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.DescriptorProto;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.FieldDescriptorProto;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.FieldDescriptorProto.Label;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.FieldDescriptorProto.Type;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.FileDescriptorProto;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.MessageOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that a {@code map<K,V>} loaded from a {@code protoc} descriptor set --
 * where the map has already been desugared into a nested {@code map_entry} message
 * plus a {@code repeated} field -- is re-exposed idiomatically as a map, matching the
 * {@code .proto} path. See [dataformats-binary#712].
 */
public class DescriptorMapField712Test extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = new ProtobufMapper();

    private static FieldDescriptorProto field(String name, int num, Label label,
            Type type, String typeName)
    {
        FieldDescriptorProto f = new FieldDescriptorProto();
        f.name = name;
        f.number = num;
        f.label = label;
        f.type = type;
        f.type_name = typeName;
        return f;
    }

    // Builds the descriptor equivalent of:
    //   message Msg {
    //     map<string, int32> counts = 1;   // => repeated CountsEntry + nested entry
    //     string name = 2;
    //   }
    private FileDescriptorSet buildDescriptorWithMap()
    {
        DescriptorProto entry = new DescriptorProto();
        entry.name = "CountsEntry";
        entry.field = new FieldDescriptorProto[] {
                field("key", 1, Label.LABEL_OPTIONAL, Type.TYPE_STRING, null),
                field("value", 2, Label.LABEL_OPTIONAL, Type.TYPE_INT32, null)
        };
        MessageOptions opts = new MessageOptions();
        opts.map_entry = true;
        entry.options = opts;

        DescriptorProto msg = new DescriptorProto();
        msg.name = "Msg";
        msg.field = new FieldDescriptorProto[] {
                field("counts", 1, Label.LABEL_REPEATED, Type.TYPE_MESSAGE, ".Msg.CountsEntry"),
                field("name", 2, Label.LABEL_OPTIONAL, Type.TYPE_STRING, null)
        };
        msg.nested_type = new DescriptorProto[] { entry };

        FileDescriptorProto fdp = new FileDescriptorProto();
        fdp.name = "test.proto";
        fdp.setPackage("");
        fdp.syntax = "proto3";
        fdp.message_type = new DescriptorProto[] { msg };

        return new FileDescriptorSet(new FileDescriptorProto[] { fdp });
    }

    @Test
    public void testDescriptorMapResolvesAsMap() throws Exception
    {
        ProtobufSchema schema = buildDescriptorWithMap().schemaFor("Msg");
        ProtobufField f = schema.getRootType().field("counts");
        assertNotNull(f);
        assertTrue(f.isMap, "'counts' from a map_entry descriptor should resolve as a map");
        assertEquals(FieldType.STRING, f.getKeyField().type);
        assertEquals(FieldType.VINT32_STD, f.getValueField().type);
    }

    // A `map_entry`-flagged message with a missing or ill-typed key/value is not something
    // protoc emits, but a hand-built or corrupt descriptor can carry one. It must fail as
    // a schema error, not as a NullPointerException from the codec later on.
    @Test
    public void testMalformedMapEntryDescriptorRejected() throws Exception
    {
        // no fields at all
        _verifyBadEntry(new FieldDescriptorProto[] { },
                "must declare both 'key'");
        // `value` only
        _verifyBadEntry(new FieldDescriptorProto[] {
                        field("value", 2, Label.LABEL_OPTIONAL, Type.TYPE_INT32, null) },
                "'key' is missing");
        // `key` only
        _verifyBadEntry(new FieldDescriptorProto[] {
                        field("key", 1, Label.LABEL_OPTIONAL, Type.TYPE_STRING, null) },
                "'value' is missing");
        // key of a type protobuf does not permit for maps
        _verifyBadEntry(new FieldDescriptorProto[] {
                        field("key", 1, Label.LABEL_OPTIONAL, Type.TYPE_DOUBLE, null),
                        field("value", 2, Label.LABEL_OPTIONAL, Type.TYPE_INT32, null) },
                "map keys must be");
    }

    private void _verifyBadEntry(FieldDescriptorProto[] entryFields, String expectedMsg)
        throws Exception
    {
        DescriptorProto entry = new DescriptorProto();
        entry.name = "CountsEntry";
        entry.field = entryFields;
        MessageOptions opts = new MessageOptions();
        opts.map_entry = true;
        entry.options = opts;

        DescriptorProto msg = new DescriptorProto();
        msg.name = "Msg";
        msg.field = new FieldDescriptorProto[] {
                field("counts", 1, Label.LABEL_REPEATED, Type.TYPE_MESSAGE, ".Msg.CountsEntry")
        };
        msg.nested_type = new DescriptorProto[] { entry };

        FileDescriptorProto fdp = new FileDescriptorProto();
        fdp.name = "test.proto";
        fdp.setPackage("");
        fdp.syntax = "proto3";
        fdp.message_type = new DescriptorProto[] { msg };

        try {
            new FileDescriptorSet(new FileDescriptorProto[] { fdp }).schemaFor("Msg");
            fail("Should not accept malformed `map_entry` message");
        } catch (IllegalArgumentException e) {
            verifyException(e, expectedMsg);
        }
    }

    @Test
    public void testDescriptorMapRoundtrip() throws Exception
    {
        ProtobufSchema schema = buildDescriptorWithMap().schemaFor("Msg");

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("x", 7);
        counts.put("y", 9);
        root.put("counts", counts);
        root.put("name", "hi");

        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(root);
        JsonNode tree = MAPPER.readerFor(JsonNode.class).with(schema).readValue(doc);
        assertEquals(7, tree.get("counts").get("x").asInt());
        assertEquals(9, tree.get("counts").get("y").asInt());
        assertEquals("hi", tree.get("name").asText());
    }
}
