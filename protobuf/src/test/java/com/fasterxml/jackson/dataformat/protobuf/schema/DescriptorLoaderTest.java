package com.fasterxml.jackson.dataformat.protobuf.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;

import java.io.InputStream;
import java.io.StringReader;

public class DescriptorLoaderTest extends ProtobufTestBase
{

    private final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

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
        ProtobufSchema schema = ProtobufSchemaLoader.std.load(new StringReader(mergedProto));

        final ObjectWriter w = MAPPER.writerFor(Main.class).with(schema);
        Other o = new Other(123);
        Main m = new Main(o);
        byte[] bytes = w.writeValueAsBytes(m);
        assertNotNull(bytes);

        // Deserialize the bytes using the descriptor
        // load main.desc descriptor file.  This file was created by protoc - o main.desc main.proto other.proto
        InputStream in = this.getClass().getResourceAsStream("/main.desc");
        FileDescriptorSet fds = DescriptorLoader.std.load(in);
        ProtobufSchema schema2 = fds.forType("Main");

        Main t = MAPPER.readerFor(Main.class).with(schema2).readValue(bytes);
        assertNotNull(t);
        assertEquals(123, t.o.f);

    }
}

