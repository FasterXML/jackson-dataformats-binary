package com.fasterxml.jackson.dataformat.protobuf.failing;

import java.util.UUID;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class GenerateSchema68Test extends ProtobufTestBase
{
    static class UUIDBean
    {
        public UUID messageId;
    }

    static class ShortBean
    {
        public short version;
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
}
