package com.fasterxml.jackson.dataformat.protobuf.failing;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class NestedWrite67Test extends ProtobufTestBase
{
    @JsonPropertyOrder({ "value", "level2" })
    public static class Level1 {
      public int value;
      public Level2 level2;
    }

    @JsonPropertyOrder({ "value", "level3s" })
    public static class Level2 {
        public int value;

        public Level3[] level3s;
    }

    public static class Level3 {
        public int value;
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

      level1.value = 1;
      level2.value = 2;
      level3a.value = 3;
      level3b.value = 4;
      Level3[] level3s = new Level3[] { level3a, level3b };

      level1.level2 = level2;
      level2.level3s = level3s;

      byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(level1);

      showBytes(bytes);

      Level1 gotLevel1 = MAPPER.readerFor(Level1.class).with(schema).readValue(bytes);

//      byte[] correct = new byte[]{0x08, 0x01, 0x12, 0x0a, 0x08, 0x02, 0x12, 0x02, 0x08, 0x03, 0x12, 0x02, 0x08, 0x04};
//      Level1 gotLevel1 = mapper.readerFor(Level1.class).with(schema).readValue(new ByteArrayInputStream(correct));

      assertEquals(level1.value, gotLevel1.value);
      assertEquals(level2.value, gotLevel1.level2.value);
      assertEquals(level3s.length, gotLevel1.level2.level3s.length);
      assertEquals(level3a.value, gotLevel1.level2.level3s[0].value);
      assertEquals(level3b.value, gotLevel1.level2.level3s[1].value);
    }

    private void showBytes(byte[] bytes) {
        for (byte b : bytes) {
          System.out.print(String.format("%8s", Integer.toHexString(b)).substring(6, 8).replaceAll(" ", "0") + " ");
        }
        System.out.println();
      }

}
