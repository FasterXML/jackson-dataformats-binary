package com.fasterxml.jackson.dataformat.avro.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroDecimal;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import junit.framework.TestCase;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.math.BigDecimal;

public class DecimalTest extends TestCase {
  static class RequiredBytesDecimal {
    @JsonProperty(required = true)
    @AvroDecimal(precision = 3, scale = 3)
    public BigDecimal value;
  }

  static class OptionalBytesDecimal {
    @AvroDecimal(precision = 3, scale = 3)
    public BigDecimal value;
  }

  static class RequiredFixedDecimal {
    @JsonProperty(required = true)
    @AvroDecimal(precision = 3, scale = 3, fixedSize = 8, typeNamespace = "com.foo.example", typeName = "Decimal", schemaType = Schema.Type.FIXED)
    public BigDecimal value;
  }

  static class OptionalFixedDecimal {
    @AvroDecimal(precision = 3, scale = 3, fixedSize = 8, typeNamespace = "com.foo.example", typeName = "Decimal", schemaType = Schema.Type.FIXED)
    public BigDecimal value;
  }

  public void testRequiredBytesDecimal() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(RequiredBytesDecimal.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));

    final RequiredBytesDecimal expected = new RequiredBytesDecimal();
    expected.value = BigDecimal.valueOf(123456, 3);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final RequiredBytesDecimal actual = mapper.reader(avroSchema).forType(RequiredBytesDecimal.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }

  public void testOptionalBytesDecimal() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(OptionalBytesDecimal.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));

    final OptionalBytesDecimal expected = new OptionalBytesDecimal();
    expected.value = BigDecimal.valueOf(9834780979L, 3);
    final GenericRecord expectedRecord = new GenericData.Record(avroSchema.getAvroSchema());
    expectedRecord.put("value", expected.value);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final OptionalBytesDecimal actual = mapper.reader(avroSchema).forType(OptionalBytesDecimal.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }

  public void testRequiredFixedDecimal() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(RequiredFixedDecimal.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));

    final RequiredFixedDecimal expected = new RequiredFixedDecimal();
    expected.value = BigDecimal.valueOf(123456, 3);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final RequiredFixedDecimal actual = mapper.reader(avroSchema).forType(RequiredFixedDecimal.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }

  public void testOptionalFixedDecimal() throws IOException {
    final AvroMapper mapper = new AvroMapper();
    final AvroSchema avroSchema = mapper.schemaFor(OptionalFixedDecimal.class);
    System.out.println(avroSchema.getAvroSchema().toString(true));

    final OptionalFixedDecimal expected = new OptionalFixedDecimal();
    expected.value = BigDecimal.valueOf(9834780979L, 3);
    final GenericRecord expectedRecord = new GenericData.Record(avroSchema.getAvroSchema());
    expectedRecord.put("value", expected.value);
    byte[] buffer = mapper.writer(avroSchema)
        .writeValueAsBytes(expected);
    final OptionalFixedDecimal actual = mapper.reader(avroSchema).forType(OptionalFixedDecimal.class)
        .readValue(buffer);
    assertNotNull(actual);
    assertEquals(expected.value, actual.value);
  }
}
