package com.fasterxml.jackson.dataformat.protobuf;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class ReadNestedUnknownFieldsTest extends ProtobufTestBase
{
    // [dataformats-binary#108]
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

    // [dataformats-binary#126]
    @JsonPropertyOrder({"embed", "state"})
    public static class OuterV2 {
        @JsonProperty("embed")
        public EmbedV2 embed;
        @JsonProperty("state")
        public String state;
    }

    @JsonPropertyOrder({"embed", "state"})
    public static class Outer {
        @JsonProperty("embed")
        public Embed embed;
        @JsonProperty("state")
        public String state;
    }

    @JsonPropertyOrder({"a", "b", "c", "extraField"})
    public static class EmbedV2 {
        @JsonProperty("a")
        public String a;
        @JsonProperty("b")
        public String b;
        @JsonProperty("c")
        public List<String> c;
        @JsonProperty("extraField")
        public String extraField;
    }

    @JsonPropertyOrder({"a", "b", "c"})
    public static class Embed {
        @JsonProperty("a")
        public String a;
        @JsonProperty("b")
        public String b;
        @JsonProperty("c")
        public List<String> c;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#108]
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

    // [dataformats-binary#126]
    public void testCheckEndAfterSkip() throws Exception
    {
        ProtobufMapper mapper = new ProtobufMapper();
        mapper.enable(JsonParser.Feature.IGNORE_UNDEFINED);
        ProtobufSchema schema = MAPPER.generateSchemaFor(Outer.class);
        ProtobufSchema schemaV2 = MAPPER.generateSchemaFor(OuterV2.class);

        EmbedV2 embedV2 = new EmbedV2();
        embedV2.c = Arrays.asList("c");
        embedV2.extraField = "extra";

        OuterV2 v2Expected = new OuterV2();
        v2Expected.embed = embedV2;
        v2Expected.state="state";

        // serialize type with extra field
        byte[] doc = mapper.writer(schemaV2).writeValueAsBytes(v2Expected);

//            showBytes(bout.toByteArray());

        // deserialize type with extra field
        OuterV2 v2Actual = mapper.readerFor(OuterV2.class)
                .with(schemaV2).readValue(doc);
        // this is ok
        assertEquals(v2Expected.state, v2Actual.state);

        // deserialize type without extra field
        Outer v1Actual = mapper.readerFor(Outer.class).with(schema)
                .readValue(doc);

        // Outer.state is skipped when skipping Embed.extraField
        assertEquals(v2Expected.state, v1Actual.state);
    }
}
