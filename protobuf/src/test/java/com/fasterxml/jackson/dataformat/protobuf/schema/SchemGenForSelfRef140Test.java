package com.fasterxml.jackson.dataformat.protobuf.schema;

import com.fasterxml.jackson.dataformat.protobuf.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

// for [dataformats-binary#140]
public class SchemGenForSelfRef140Test extends ProtobufTestBase
{
    public static class ForwardRefType {
        public int id;
        public BackRefType next;
    }

    public static class BackRefType {
        public int id;
        public ForwardRefType next;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

     private final ProtobufMapper MAPPER = newObjectMapper();

     public void testWithNestedClass() throws Exception
     {
          ProtobufSchema schemaWrapper = MAPPER.generateSchemaFor(ForwardRefType.class);
          assertNotNull(schemaWrapper);
     }
}
