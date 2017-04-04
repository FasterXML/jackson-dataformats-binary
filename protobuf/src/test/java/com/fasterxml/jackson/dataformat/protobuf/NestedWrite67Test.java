package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class NestedWrite67Test extends ProtobufTestBase
{
    @JsonPropertyOrder({ "value1", "level2" })
    public static class Level1 {
        @JsonProperty(index=1)
        public int value1;
        @JsonProperty(index=2)
        public Level2 level2;
    }

    @JsonPropertyOrder({ "value2", "level3s" })
    public static class Level2 {
        @JsonProperty(index=3)
        public int value2;
        @JsonProperty(index=4)
        public Level3[] level3s;
    }

    public static class Level3 {
        @JsonProperty(index=5)
        public int value3;
    }

    final ProtobufMapper MAPPER = new ProtobufMapper();

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testIssue67() throws Exception
    {
      ProtobufSchema schema = MAPPER.generateSchemaFor(Level1.class);

//      System.out.println(schema.getSource());

      Level1 level1 = new Level1();
      Level2 level2 = new Level2();
      Level3 level3a = new Level3();
      Level3 level3b = new Level3();

      level1.value1 = 1;
      level2.value2 = 2;
      level3a.value3 = 3;
      level3b.value3 = 4;
      Level3[] level3s = new Level3[] { level3a, level3b };

      level1.level2 = level2;
      level2.level3s = level3s;

      byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(level1);

//      showBytes(bytes);

      Level1 gotLevel1 = MAPPER.readerFor(Level1.class).with(schema).readValue(bytes);

//      byte[] correct = new byte[]{0x08, 0x01, 0x12, 0x0a, 0x18, 0x02,
//                                  0x22, 0x02, 0x28, 0x03, 0x22, 0x02, 0x28, 0x04};

      assertEquals(level1.value1, gotLevel1.value1);
      assertEquals(level2.value2, gotLevel1.level2.value2);

      assertEquals(level3s.length, gotLevel1.level2.level3s.length);
      assertEquals(level3a.value3, gotLevel1.level2.level3s[0].value3);
      assertEquals(level3b.value3, gotLevel1.level2.level3s[1].value3);

      assertEquals(level3s.length, gotLevel1.level2.level3s.length);
      assertEquals(level3a.value3, gotLevel1.level2.level3s[0].value3);
    }
/*
    private void showBytes(byte[] bytes) {
        for (byte b : bytes) {
          System.out.print(String.format("%8s", Integer.toHexString(b)).substring(6, 8).replaceAll(" ", "0") + " ");
        }
        System.out.println();
      }
*/
}
