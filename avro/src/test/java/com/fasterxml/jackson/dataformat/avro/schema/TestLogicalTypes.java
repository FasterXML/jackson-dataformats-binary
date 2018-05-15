package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroDate;
import com.fasterxml.jackson.dataformat.avro.AvroDecimal;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;
import com.fasterxml.jackson.dataformat.avro.AvroTimeMicrosecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimeMillisecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMicrosecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMillisecond;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.junit.Assert;

import java.math.BigDecimal;
import java.util.Date;

public class TestLogicalTypes extends AvroTestBase {

  static class BytesDecimalType {
    @JsonProperty(required = true)
    @AvroDecimal(precision = 5)
    public BigDecimal value;
  }

  static class FixedNoNameDecimalType {
    @JsonProperty(required = true)
    @AvroDecimal(precision = 5, schemaType = Schema.Type.FIXED)
    public BigDecimal value;
  }

  static class FixedDecimalType {
    @JsonProperty(required = true)
    @AvroDecimal(precision = 5, schemaType = Schema.Type.FIXED, typeName = "foo", typeNamespace = "com.fasterxml.jackson.dataformat.avro.schema", fixedSize = 8)
    public BigDecimal value;
  }

  static class TimestampMillisecondsType {
    @AvroTimestampMillisecond
    @JsonProperty(required = true)
    public Date value;
  }

  static class TimeMillisecondsType {
    @AvroTimeMillisecond
    @JsonProperty(required = true)
    public Date value;
  }

  static class TimestampMicrosecondsType {
    @AvroTimestampMicrosecond
    @JsonProperty(required = true)
    public Date value;
  }

  static class TimeMicrosecondsType {
    @AvroTimeMicrosecond
    @JsonProperty(required = true)
    public Date value;
  }
  
  static class DateType {
    @AvroDate
    @JsonProperty(required = true)
    public Date value;
  }

  AvroSchema getSchema(Class<?> cls) throws JsonMappingException {
    AvroMapper avroMapper = new AvroMapper();
    AvroSchemaGenerator avroSchemaGenerator=new AvroSchemaGenerator();
    avroMapper.acceptJsonFormatVisitor(cls, avroSchemaGenerator);
    AvroSchema schema = avroSchemaGenerator.getGeneratedSchema();
    assertNotNull("Schema should not be null.", schema);
    assertEquals(Schema.Type.RECORD, schema.getAvroSchema().getType());
    System.out.println(schema.getAvroSchema().toString(true));
    return schema;
  }

  void assertLogicalType(Schema.Field field, final Schema.Type type, final String logicalType) {
    assertEquals("schema().getType() does not match.", type, field.schema().getType());
    assertNotNull("logicalType should not be null.", field.schema().getLogicalType());
    assertEquals("logicalType does not match.", logicalType, field.schema().getLogicalType().getName());
    field.schema().getLogicalType().validate(field.schema());
  }

  public void testFixedNoNameDecimalType() throws JsonMappingException {
    try {
      AvroSchema avroSchema = getSchema(FixedNoNameDecimalType.class);
      Schema schema = avroSchema.getAvroSchema();
      Schema.Field field = schema.getField("value");
      assertLogicalType(field, Schema.Type.BYTES, "decimal");
      assertEquals(5, field.schema().getObjectProp("precision"));
      assertEquals(0, field.schema().getObjectProp("scale"));
      Assert.fail("SchemaParseException should have been thrown");
    } catch (SchemaParseException ex) {

    }
  }

  public void testBytesDecimalType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(BytesDecimalType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.BYTES, "decimal");
    assertEquals(5, field.schema().getObjectProp("precision"));
    assertEquals(0, field.schema().getObjectProp("scale"));
  }

  public void testFixedDecimalType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(FixedDecimalType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.FIXED, "decimal");
    assertEquals(5, field.schema().getObjectProp("precision"));
    assertEquals(0, field.schema().getObjectProp("scale"));
  }

  public void testTimestampMillisecondsType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(TimestampMillisecondsType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.LONG, "timestamp-millis");
  }

  public void testTimeMillisecondsType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(TimeMillisecondsType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.INT, "time-millis");
  }

  public void testTimestampMicrosecondsType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(TimestampMicrosecondsType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.LONG, "timestamp-micros");
  }

  public void testTimeMicrosecondsType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(TimeMicrosecondsType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.LONG, "time-micros");
  }
  
  public void testDateType() throws JsonMappingException {
    AvroSchema avroSchema = getSchema(DateType.class);
    Schema schema = avroSchema.getAvroSchema();
    Schema.Field field = schema.getField("value");
    assertLogicalType(field, Schema.Type.INT, "date");
  }
}
