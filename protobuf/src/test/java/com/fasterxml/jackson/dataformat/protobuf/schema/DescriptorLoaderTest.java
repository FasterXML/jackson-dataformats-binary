package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;

public class DescriptorLoaderTest extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = new ProtobufMapper();

    static class Main
    {
        public Other o;

        protected Main() { }

        public Main(Other o)
        {
            this.o = o;
        }
    }


    static class Other
    {
        public int f;

        protected Other() { }

        public Other(int f)
        {
            this.f = f;
        }
    }

    static String mergedProto =
        "syntax = \"proto2\";\n"
        + "package mypackage;\n"
        + "\n"
        + "message Main {\n"
        + "    required Other o = 1;\n"
        + "}\n"
        + "\n"
        + "message Other {\n"
        + "    required int32 f = 1;\n"
        + "}\n";

    public void testParsing() throws Exception
    {
        // create PB binary from known .proto schema
        ProtobufSchema schema = MAPPER.schemaLoader().parse(mergedProto);

        final ObjectWriter w = MAPPER.writerFor(Main.class).with(schema);
        Other o = new Other(123);
        Main m = new Main(o);
        byte[] bytes = w.writeValueAsBytes(m);
        assertNotNull(bytes);

        // Deserialize the bytes using the descriptor
        // load main.desc descriptor file.  This file was created by protoc - o main.desc main.proto other.proto
        FileDescriptorSet fds;
        try (InputStream in = this.getClass().getResourceAsStream("/main.desc")) {
            fds = MAPPER.loadDescriptorSet(in);
        }
        ProtobufSchema schema2 = fds.schemaFor("Main");

        Main t = MAPPER.readerFor(Main.class).with(schema2).readValue(bytes);
        assertNotNull(t);
        assertEquals(123, t.o.f);
    }
}
