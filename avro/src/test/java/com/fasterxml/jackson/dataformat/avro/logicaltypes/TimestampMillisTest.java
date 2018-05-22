package com.fasterxml.jackson.dataformat.avro.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMicrosecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMillisecond;
import org.apache.avro.Schema;

import java.util.Date;

public class TimestampMillisTest extends LogicalTypeTestCase<TimestampMillisTest.RequiredTimestampMillis> {

  static class RequiredTimestampMillis extends TestData<Date> {
    @JsonProperty(required = true)
    @AvroTimestampMillisecond
    Date value;

    @Override
    public Date value() {
      return this.value;
    }
  }

  @Override
  protected Class<RequiredTimestampMillis> dataClass() {
    return RequiredTimestampMillis.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.LONG;
  }

  @Override
  protected String logicalType() {
    return "timestamp-millis";
  }

  @Override
  protected RequiredTimestampMillis testData() {
    RequiredTimestampMillis v = new RequiredTimestampMillis();
    v.value = new Date(1526943920123L);
    return v;
  }

  @Override
  protected Object convertedValue() {
    return 1526943920123L;
  }
}
