package com.fasterxml.jackson.dataformat.avro.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMillisecond;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Date;

public class TimestampMillisTestOld extends TestCase {

  static class RequiredTimestampMillis {
    @JsonProperty(required = true)
    @AvroTimestampMillisecond
    public Date value;
  }


  public void testRoundtrip() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(RequiredTimestampMillis.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));
    final RequiredTimestampMillis expected = new RequiredTimestampMillis();
    expected.value = new Date(1526943920123L);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final RequiredTimestampMillis actual = mapper.reader(avroSchema).forType(RequiredTimestampMillis.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }

}
