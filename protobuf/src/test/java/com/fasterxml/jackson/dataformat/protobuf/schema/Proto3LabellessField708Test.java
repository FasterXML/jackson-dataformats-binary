package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#708]: proto3 "singular" fields are declared without a
// leading `required`/`optional`/`repeated` label, which the bundled
// protoparser 4.0.3 grammar rejected. A preprocessing pass injects a synthetic
// `optional` label so these parse (and resolve as singular fields).
public class Proto3LabellessField708Test extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = new ProtobufMapper();

    @Test
    public void testLabellessScalarFields() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Point {\n"
                + "  int32 x = 1;\n"
                + "  string label = 2;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto);
        ProtobufMessage msg = schema.getRootType();
        assertEquals("Point", msg.getName());

        ProtobufField x = msg.field("x");
        assertNotNull(x);
        assertFalse(x.required);
        assertFalse(x.repeated);
        ProtobufField label = msg.field("label");
        assertNotNull(label);
        assertFalse(label.required);
        assertFalse(label.repeated);
    }

    @Test
    public void testLabellessMessageTypedField() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Inner {\n"
                + "  int32 a = 1;\n"
                + "}\n"
                + "message Outer {\n"
                + "  Inner inner = 1;\n"
                + "  string name = 2;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto, "Outer");
        ProtobufMessage msg = schema.getRootType();
        assertEquals("Outer", msg.getName());
        assertNotNull(msg.field("inner"));
        assertNotNull(msg.field("name"));
    }

    @Test
    public void testLabellessFieldInNestedMessage() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Outer {\n"
                + "  message Inner {\n"
                + "    int32 a = 1;\n"
                + "  }\n"
                + "  Inner inner = 1;\n"
                + "  string name = 2;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto, "Outer");
        assertNotNull(schema.getRootType().field("inner"));
        assertNotNull(schema.getRootType().field("name"));
    }

    // `oneof` fields are already label-less in proto2 and were parsed fine;
    // the preprocessor must NOT inject a label inside a oneof body.
    @Test
    public void testOneOfBodyUntouched() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  oneof choice {\n"
                + "    string a = 1;\n"
                + "    int32 b = 2;\n"
                + "  }\n"
                + "  string outside = 3;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto);
        ProtobufMessage msg = schema.getRootType();
        assertNotNull(msg.field("a"));
        assertNotNull(msg.field("b"));
        assertNotNull(msg.field("outside"));
    }

    // enum constants must not receive an injected label
    @Test
    public void testEnumBodyUntouched() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "enum Corpus {\n"
                + "  UNIVERSAL = 0;\n"
                + "  WEB = 1;\n"
                + "}\n"
                + "message Msg {\n"
                + "  Corpus corpus = 1;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto, "Msg");
        assertNotNull(schema.getRootType().field("corpus"));
    }

    @Test
    public void testRepeatedLabelPreserved() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  repeated int32 values = 1;\n"
                + "  string name = 2;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto);
        ProtobufField values = schema.getRootType().field("values");
        assertNotNull(values);
        assertTrue(values.repeated);
    }

    // A type keyword appearing inside a comment must not be treated as a field
    @Test
    public void testTypeWordInCommentIgnored() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  // string commented = 9;\n"
                + "  string real = 1;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto);
        assertNotNull(schema.getRootType().field("real"));
    }

    // proto2 semantics must be untouched: label-less fields remain an error
    @Test
    public void testProto2LabellessStillFails() throws Exception
    {
        final String proto = "syntax = \"proto2\";\n"
                + "message Msg {\n"
                + "  string name = 1;\n"
                + "}\n";
        try {
            ProtobufSchemaLoader.std.parse(proto);
            fail("Should not pass: proto2 requires explicit field labels");
        } catch (Exception e) {
            verifyException(e, "unexpected label");
        }
    }

    // Map fields are not yet supported; must fail with a clear, dedicated message
    @Test
    public void testMapFieldClearError() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Msg {\n"
                + "  map<string, int32> counts = 1;\n"
                + "}\n";
        try {
            ProtobufSchemaLoader.std.parse(proto);
            fail("Should not pass: map fields are not yet supported");
        } catch (IllegalArgumentException e) {
            verifyException(e, "map");
            verifyException(e, "not yet supported");
        }
    }

    // `map` is valid (and label-less) in proto2 too, so the same clear error
    // must be raised there -- not protoparser's cryptic "unexpected label: map"
    @Test
    public void testProto2MapFieldClearError() throws Exception
    {
        final String proto = "syntax = \"proto2\";\n"
                + "message Msg {\n"
                + "  map<string, int32> counts = 1;\n"
                + "}\n";
        try {
            ProtobufSchemaLoader.std.parse(proto);
            fail("Should not pass: map fields are not yet supported");
        } catch (IllegalArgumentException e) {
            verifyException(e, "map");
            verifyException(e, "not yet supported");
        }
    }

    // End-to-end: label-less proto3 schema must encode and decode correctly
    @Test
    public void testLabellessRoundTrip() throws Exception
    {
        final String proto = "syntax = \"proto3\";\n"
                + "message Point {\n"
                + "  string name = 1;\n"
                + "  int32 x = 2;\n"
                + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(proto);

        Map<String,Object> input = new LinkedHashMap<String,Object>();
        input.put("name", "Bob");
        input.put("x", 42);

        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(input);
        @SuppressWarnings("unchecked")
        Map<String,Object> decoded = MAPPER.readerFor(Map.class).with(schema).readValue(encoded);

        assertEquals("Bob", decoded.get("name"));
        assertEquals(Integer.valueOf(42), decoded.get("x"));
    }
}
