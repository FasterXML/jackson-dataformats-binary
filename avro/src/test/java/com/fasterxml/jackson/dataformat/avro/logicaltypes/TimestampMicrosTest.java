package com.fasterxml.jackson.dataformat.avro.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMicrosecond;
import junit.framework.TestCase;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;

import java.io.IOException;
import java.util.Date;

public class TimestampMicrosTest extends LogicalTypeTestCase<TimestampMicrosTest.RequiredTimestampMicros> {

  static class RequiredTimestampMicros extends TestData<Date> {
    @JsonProperty(required = true)
    @AvroTimestampMicrosecond
    Date value;

    @Override
    public Date value() {
      return this.value;
    }
  }

  static final Date VALUE = new Date(1526943920123L);

  @Override
  protected Class<RequiredTimestampMicros> dataClass() {
    return RequiredTimestampMicros.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.LONG;
  }

  @Override
  protected String logicalType() {
    return "timestamp-micros";
  }

  @Override
  protected RequiredTimestampMicros testData() {
    RequiredTimestampMicros v = new RequiredTimestampMicros();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return VALUE.getTime() * 1000L;
  }
}
