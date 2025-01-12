package com.fasterxml.jackson.dataformat.protobuf.schema;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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

     @Test
    public void testWithNestedClass() throws Exception
     {
          ProtobufSchema schemaWrapper = MAPPER.generateSchemaFor(ForwardRefType.class);
          assertNotNull(schemaWrapper);
     }
}
