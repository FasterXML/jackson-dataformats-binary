package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.core.JsonParser;

import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

// [dataformats-binary#108]
public class ReadNestedUnknownFieldsTest extends ProtobufTestBase
{
    public static class LessNestedField {
        @JsonProperty(value = "f1", index = 1)
        private NestedOneField f1;

        public NestedOneField getF1() {
          return f1;
        }

        public void setF1(NestedOneField f1) {
          this.f1 = f1;
        }
    }

    public static class MoreNestedField {

        @JsonProperty(value = "f1", index = 1)
        private NestedTwoField f1;

        public NestedTwoField getF1() {
          return f1;
        }

        public void setF1(NestedTwoField f1) {
          this.f1 = f1;
        }
    }

    public static class NestedOneField {

        @JsonProperty(value = "nested2", index = 2)
        private int nested2;

        public int getNested2() {
          return nested2;
        }

        public void setNested2(int nested2) {
          this.nested2 = nested2;
        }
    }

    public static class NestedTwoField {

        @JsonProperty(value = "nested1", index = 1)
        private int nested1;

        @JsonProperty(value = "nested2", index = 2)
        private int nested2;

        public int getNested1() {
          return nested1;
        }

        public void setNested1(int nested1) {
          this.nested1 = nested1;
        }

        public int getNested2() {
          return nested2;
        }

        public void setNested2(int nested2) {
          this.nested2 = nested2;
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
          MoreNestedField moreNestedField = new MoreNestedField();
          NestedTwoField nestedTwoField = new NestedTwoField();
          nestedTwoField.setNested1(1);
          nestedTwoField.setNested2(2);
          moreNestedField.setF1(nestedTwoField);

          byte[] in = MAPPER.writerFor(MoreNestedField.class)
                  .with(MAPPER.generateSchemaFor(MoreNestedField.class))
                  .writeValueAsBytes(moreNestedField);

          LessNestedField lesser = MAPPER.readerFor(LessNestedField.class)
                  .with(MAPPER.generateSchemaFor(LessNestedField.class))
                  // important: skip through unknown
                  .with(JsonParser.Feature.IGNORE_UNDEFINED)
                  .readValue(in);

          assertEquals(moreNestedField.getF1().getNested2(), lesser.getF1().getNested2());
    }
}
