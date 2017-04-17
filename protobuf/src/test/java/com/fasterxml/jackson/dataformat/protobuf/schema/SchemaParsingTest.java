package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.List;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.NativeProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufField;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufMessage;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class SchemaParsingTest extends ProtobufTestBase
{
    final protected static String PROTOC_ENUMS =
            "message Enums {\n"
            +" enum StdEnum {\n"
            +"   A = 0;\n"
            +"   B = 1;\n"
            +" }\n"
            +" enum NonStdEnum {\n"
            +"   C = 10;\n"
            +"   D = 20;\n"
            +" }\n"
            +" optional StdEnum enum1 = 1;\n"
            +" optional NonStdEnum enum2 = 2;\n"
            +"}\n"
    ;

    final protected static String PROTOC_EMPTY = "message Empty { }";

    final protected static String PROTOC_STRINGS_PACKED =
            "message Strings {\n"
            +" repeated string values = 2 [packed=true]; /* comment */\n"
            +"}\n"
    ;

    public void testSimpleSearchRequest() throws Exception
    {
        // First: with implicit first type:
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_SEARCH_REQUEST);
        assertNotNull(schema);

        // then with named, step by step
        NativeProtobufSchema nat = ProtobufSchemaLoader.std.parseNative(PROTOC_SEARCH_REQUEST);
        assertNotNull(nat);
        assertNotNull(nat.forFirstType());
        assertNotNull(nat.forType("SearchRequest"));

        List<String> all = nat.getMessageNames();
        assertEquals(1, all.size());
        assertEquals("SearchRequest", all.get(0));
        ProtobufMessage msg = schema.getRootType();
        assertEquals(4, msg.getFieldCount());

        _verifyMessageFieldLinking(msg);
    }

    public void testBoxAndPoint() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX);
        assertNotNull(schema);
        List<String> all = schema.getMessageTypes();
        assertEquals(2, all.size());
        assertTrue(all.contains("Box"));
        assertTrue(all.contains("Point"));
        _verifyMessageFieldLinking(schema.getRootType());
    }

    public void testRecursive() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_NODE);
        assertNotNull(schema);
        List<String> all = schema.getMessageTypes();
        assertEquals(1, all.size());
        assertEquals("Node", all.get(0));
        ProtobufMessage msg = schema.getRootType();
        assertEquals(3, msg.getFieldCount());
        ProtobufField f = msg.field("id");
        assertNotNull(f);
        assertEquals("id", f.name);

        _verifyMessageFieldLinking(schema.getRootType());
    }

    public void testEnum() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_ENUMS);
        assertNotNull(schema);
        List<String> all = schema.getMessageTypes();
        assertEquals(1, all.size());

        ProtobufMessage msg = schema.getRootType();
        assertEquals(2, msg.getFieldCount());

        _verifyMessageFieldLinking(msg);

        ProtobufField f;
        
        f = msg.field("enum1");
        assertNotNull(f);
        assertTrue(f.isStdEnum);
        assertFalse(f.packed);
        
        f = msg.field("enum2");
        assertNotNull(f);
        assertFalse(f.isStdEnum);
    }

    public void testEmpty() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_EMPTY);
        assertNotNull(schema);
        List<String> all = schema.getMessageTypes();
        assertEquals(1, all.size());
    }

    public void testPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRINGS_PACKED);
        assertNotNull(schema);
        List<String> all = schema.getMessageTypes();
        assertEquals(1, all.size());
        ProtobufMessage msg = schema.getRootType();
        assertEquals(1, msg.getFieldCount());
        ProtobufField field = msg.fields().iterator().next();
        assertEquals("values", field.name);
        assertEquals(2, field.id);
        assertTrue(field.packed);
    }
}

