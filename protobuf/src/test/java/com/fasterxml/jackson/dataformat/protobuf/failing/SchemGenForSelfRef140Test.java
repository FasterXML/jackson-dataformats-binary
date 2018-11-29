package com.fasterxml.jackson.dataformat.protobuf.failing;

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
    static class B {
        public boolean isC() {
            return c;
        }

        public void setC(boolean c) {
            this.c = c;
        }

        private boolean c = true;

    }

    static class A {
        private OffsetDateTime date;
        private List<B> b = Arrays.asList(new B[] { new B(), new B() });
        private B[] b2 = new B[] { new B(), new B() };

        public List<B> getB() {
            return b;
        }

        public void setB(List<B> b) {
            this.b = b;
        }

        public B[] getB2() {
            return b2;
        }

        public void setB2(B[] b2) {
            this.b2 = b2;
        }

        public OffsetDateTime getDate() {
            return date;
        }

        public void setDate(OffsetDateTime date) {
            this.date = date;
        }
    }
*/
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
