package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class ReadUnkownFieldsTest extends ProtobufTestBase
{
    static class OneField {

        @JsonProperty(value = "f3", index = 3)
        private int f3;

        public int getF3() {
          return f3;
        }

        public void setF3(int f3) {
          this.f3 = f3;
        }
      }

      static class ThreeField {

        @JsonProperty(value = "f1", index = 1)
        private int f1;

        @JsonProperty(value = "f2", index = 2)
        private int f2;

        @JsonProperty(value = "f3", index = 3)
        private int f3;

        public int getF1() {
          return f1;
        }

        public void setF1(int f1) {
          this.f1 = f1;
        }

        public int getF2() {
          return f2;
        }

        public void setF2(int f2) {
          this.f2 = f2;
        }

        public int getF3() {
          return f3;
        }

        public void setF3(int f3) {
          this.f3 = f3;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

    public void testMultipleUnknown() throws Exception
    {
        ThreeField threeField = new ThreeField();
        threeField.setF1(1);
        threeField.setF2(2);
        threeField.setF3(3);

        ProtobufSchema schemaWith3 = MAPPER.generateSchemaFor(ThreeField.class);
        byte[] in = MAPPER.writer(schemaWith3)
                .writeValueAsBytes(threeField);

        ProtobufSchema schemaWith1 = MAPPER.generateSchemaFor(OneField.class);
        OneField oneField = MAPPER.readerFor(OneField.class).with(schemaWith1)
                // important: skip through unknown
                .with(JsonParser.Feature.IGNORE_UNDEFINED)
                .readValue(in);

        assertEquals(threeField.getF3(), oneField.getF3());
    }
}
