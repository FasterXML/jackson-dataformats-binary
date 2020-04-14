package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.protobuf.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

// [dataformats-binary#202]
public class ReadUnknownFields202Test extends ProtobufTestBase
{
    // [dataformats-binary#202]
    static class TestMessageV0
    {
        @JsonProperty(required = true, index = 1)
        private String id;
        @JsonProperty(required = false, index = 2)
        private String plant;

        public TestMessageV0() { }

        public TestMessageV0(String id, String plant) {
          this.id = id;
          this.plant = plant;
        }

        public String getId() {
          return id;
        }

        public String getPlant() {
          return plant;
        }
    }

    static class TestMessageV1
    {
        @JsonProperty(required = true, index = 1)
        private String id;
        @JsonProperty(required = false, index = 2)
        private String plant;
        @JsonProperty(required = false, index = 3)
        private Double length;
        @JsonProperty(required = false, index = 4)
        private Double width;
        @JsonProperty(required = false, index = 5)
        private String descr;
        @JsonProperty(required = false, index = 6)
        private String source;

        public TestMessageV1() { }

        public TestMessageV1(String id, String plant, Double length) {
          this.id = id;
          this.plant = plant;
          this.length = length;
        }

        public String getId() {
          return id;
        }

        public String getPlant() {
          return plant;
        }

        public Double getLength() {
          return length;
        }

        public Double getWidth() {
          return width;
        }

        public String getDescr() {
          return descr;
        }

        public String getSource() {
          return source;
        }
    }

    // [dataformats-binary#202]
    private static final String MESSAGE_V0_SCHEMA =
            "message TestMessageV0 {\n"
            + "required string id = 1;\n"
            + "optional string plant = 2;\n"
            +"}";

    private static final String MESSAGE_V1_SCHEMA =
        "message TestMessageV1 {\n" +
           "required string id = 1;\n" +
           "optional double length = 3;\n" +
           "optional double width = 4;\n" +
           "optional string source = 6;\n" +
           "optional string descr = 5;\n" +
           "optional string plant = 2;\n" +
           "}";

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    // [dataformats-binary#202]
    public void testV1toV0() throws Exception {
        final ProtobufMapper MAPPER = newMapperBuilder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(JsonParser.Feature.IGNORE_UNDEFINED)
                .build();

        TestMessageV1 messageV1 = new TestMessageV1("1", "test", 9.9);
        ProtobufSchema schemaV1 = ProtobufSchemaLoader.std.parse(MESSAGE_V1_SCHEMA);

        byte[] protobufData = MAPPER
                .writer(schemaV1)
                .writeValueAsBytes(messageV1);

        ProtobufSchema schemaV0 = ProtobufSchemaLoader.std.parse(MESSAGE_V0_SCHEMA);
        TestMessageV0 messageV0 = MAPPER
                .readerFor(TestMessageV0.class)
                .with(schemaV0)
                .readValue(protobufData);

        assertEquals(messageV1.getId(), messageV0.getId());
        assertEquals(messageV1.getPlant(), messageV0.getPlant());
    }
}
