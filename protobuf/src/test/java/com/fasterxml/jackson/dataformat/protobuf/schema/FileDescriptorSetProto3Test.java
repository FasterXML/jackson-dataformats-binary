package com.fasterxml.jackson.dataformat.protobuf.schema;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.DescriptorProto;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.FieldDescriptorProto;
import com.fasterxml.jackson.dataformat.protobuf.schema.FileDescriptorSet.FileDescriptorProto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// [dataformats-binary#134]: `FileDescriptorProto.getSyntax()` crashed on real
// (lowercase) protoc-produced "proto3" syntax strings, since the enum
// constants are PROTO_2/PROTO_3, not proto2/proto3.
public class FileDescriptorSetProto3Test extends ProtobufTestBase
{
    private FileDescriptorProto messageWithRepeatedInt32(String syntax)
    {
        FieldDescriptorProto f = new FieldDescriptorProto();
        f.name = "values";
        f.number = 1;
        f.label = FieldDescriptorProto.Label.LABEL_REPEATED;
        f.type = FieldDescriptorProto.Type.TYPE_INT32;

        DescriptorProto msg = new DescriptorProto();
        msg.name = "Msg";
        msg.field = new FieldDescriptorProto[] { f };

        FileDescriptorProto fdp = new FileDescriptorProto();
        fdp.name = "test.proto";
        fdp.setPackage("test");
        fdp.syntax = syntax;
        fdp.message_type = new DescriptorProto[] { msg };
        return fdp;
    }

    @Test
    public void testProto3SyntaxStringDoesNotCrashAndDefaultsToPacked() throws Exception
    {
        FileDescriptorSet fds = new FileDescriptorSet(
                new FileDescriptorProto[] { messageWithRepeatedInt32("proto3") });
        ProtobufSchema schema = fds.schemaFor("Msg");
        assertTrue(schema.getRootType().field("values").packed);
    }

    @Test
    public void testProto2SyntaxStringStaysUnpacked() throws Exception
    {
        FileDescriptorSet fds = new FileDescriptorSet(
                new FileDescriptorProto[] { messageWithRepeatedInt32("proto2") });
        ProtobufSchema schema = fds.schemaFor("Msg");
        assertFalse(schema.getRootType().field("values").packed);
    }
}
