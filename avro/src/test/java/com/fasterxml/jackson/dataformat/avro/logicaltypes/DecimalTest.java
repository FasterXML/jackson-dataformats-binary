package com.fasterxml.jackson.dataformat.avro.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroDecimal;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import junit.framework.TestCase;

import java.io.IOException;
import java.math.BigDecimal;

public class DecimalTest extends TestCase {
  static class RequiredDecimal {
    @JsonProperty(required = true)
    @AvroDecimal(precision = 3, scale = 3)
    public BigDecimal value;
  }

  static class OptionalDecimal {
    @AvroDecimal(precision = 3, scale = 3)
    public BigDecimal value;    
  }
  
  public void testRequired() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(RequiredDecimal.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));

    final RequiredDecimal expected = new RequiredDecimal();
    expected.value = BigDecimal.valueOf(123456, 3);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final RequiredDecimal actual = mapper.reader(avroSchema).forType(RequiredDecimal.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }

  public void testOptional() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(OptionalDecimal.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));

    final OptionalDecimal expected = new OptionalDecimal();
    expected.value = BigDecimal.valueOf(123456, 3);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final OptionalDecimal actual = mapper.reader(avroSchema).forType(OptionalDecimal.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }
}
