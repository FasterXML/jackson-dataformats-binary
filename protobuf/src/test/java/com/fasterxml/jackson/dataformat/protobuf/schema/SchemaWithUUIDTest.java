package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class SchemaWithUUIDTest extends ProtobufTestBase
{
    static class UUIDBean
    {
//        @JsonProperty(required = true, index = 1)
        public UUID messageId;
    }

    static class ShortBean
    {
        @JsonProperty(index = 1)
        public short version;
    }

    static class BinaryBean
    {
        @JsonProperty(index = 2)
        public byte[] data;

        @JsonProperty(index = 3)
        public ByteBuffer extraData;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#68]
    public void testWithUUID() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(UUIDBean.class);
        assertNotNull(schema);
    }

    // [dataformats-binary#68]
    public void testWithShort() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(ShortBean.class);
        assertNotNull(schema);
    }

    public void testWithBinary() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(BinaryBean.class);
        assertNotNull(schema);
    }
}
