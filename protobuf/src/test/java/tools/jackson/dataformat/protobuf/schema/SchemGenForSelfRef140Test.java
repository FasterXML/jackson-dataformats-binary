package tools.jackson.dataformat.protobuf.schema;

import tools.jackson.dataformat.protobuf.*;

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
