package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.java8.AvroJavaTimeModule;
import junit.framework.TestCase;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public abstract class LogicalTypeTestCase<T extends TestData> extends TestCase {
  protected AvroMapper mapper;
  protected AvroSchema avroSchema;
  protected Schema recordSchema;
  protected Schema schema;
  protected Class<T> dataClass;
  protected Schema.Type schemaType;
  protected String logicalType;

  protected abstract Class<T> dataClass();

  protected abstract Schema.Type schemaType();

  protected abstract String logicalType();

  protected abstract T testData();

  protected abstract Object convertedValue();


  @Override
  protected void setUp() throws Exception {
    this.mapper = new AvroMapper(new AvroJavaTimeModule());
    this.dataClass = dataClass();

    this.avroSchema = mapper.schemaFor(this.dataClass);
    assertNotNull("AvroSchema should not be null", this.avroSchema);
    this.recordSchema = this.avroSchema.getAvroSchema();
    assertNotNull("Schema should not be null", this.recordSchema);
    assertEquals("Schema should be a record.", Schema.Type.RECORD, this.recordSchema.getType());
    Schema.Field field = this.recordSchema.getField("value");
    assertNotNull("schema must have a 'value' field", field);
    this.schema = field.schema();
    this.schemaType = schemaType();
    this.logicalType = logicalType();

    System.out.println(recordSchema.toString(true));
    configure(this.mapper);
  }

  protected void configure(AvroMapper mapper) {

  }

  public void testSchemaType() {
    assertEquals("schema.getType() does not match.", this.schemaType, this.schema.getType());
  }

  public void testLogicalType() {
    assertNotNull("schema.getLogicalType() should not return null", this.schema.getLogicalType());
    assertEquals("schema.getLogicalType().getName() does not match.", this.logicalType, this.schema.getLogicalType().getName());
  }

  byte[] serialize(T expected) throws JsonProcessingException {
    final byte[] actualbytes = this.mapper.writer(this.avroSchema).writeValueAsBytes(expected);
    return actualbytes;
  }

  public void testRoundTrip() throws IOException {
    final T expected = testData();
    final byte[] actualbytes = serialize(expected);
    final T actual = this.mapper.reader(avroSchema).forType(this.dataClass).readValue(actualbytes);
    assertNotNull("actual should not be null.", actual);
    assertEquals(expected.value(), actual.value());
  }

  public void testAvroSerialization() throws IOException {
    final T expected = testData();
    final byte[] actualbytes = serialize(expected);
    final Object convertedValue = convertedValue();
    byte[] expectedBytes;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(outputStream, null);
      GenericData.Record record = new GenericData.Record(this.recordSchema);
      record.put("value", convertedValue);
      DatumWriter writer = new GenericDatumWriter(this.recordSchema);
      writer.write(record, encoder);
      expectedBytes = outputStream.toByteArray();
    }

    assertTrue("serialized output does not match avro version.", Arrays.equals(expectedBytes, actualbytes));
  }
}
