package com.fasterxml.jackson.dataformat.protobuf.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;

import java.io.InputStream;
import java.io.StringReader;

public class DescriptorLoaderCompleteTest extends ProtobufTestBase
{

    private final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());


    public void testParsing() throws Exception
    {
        // load unittest.desc descriptor file.
        // This file was created at the top directory of Protobuf project.
        // protoc - o unittest_proto3.desc google/protobuf/unittest_proto3.proto
        InputStream in = this.getClass().getResourceAsStream("/unittest_proto3.desc");
        FileDescriptorSet fds = DescriptorLoader.std.load(in);
        ProtobufSchema schema2 = fds.forType("TestAllTypes");
        assertNotNull(schema2);
    }
}
