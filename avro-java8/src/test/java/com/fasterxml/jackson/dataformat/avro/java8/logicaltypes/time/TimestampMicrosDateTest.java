package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMicrosecond;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.util.Date;

public class TimestampMicrosDateTest extends LogicalTypeTestCase<TimestampMicrosDateTest.TestCase> {
  @Override
  protected Class<TestCase> dataClass() {
    return TestCase.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.LONG;
  }

  @Override
  protected String logicalType() {
    return "timestamp-micros";
  }

  static final Date VALUE = new Date(1526955327123L);

  @Override
  protected TestCase testData() {
    TestCase v = new TestCase();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return 1526955327123L * 1000L;
  }

  static class TestCase extends TestData<Date> {
    @JsonProperty(required = true)
    @AvroTimestampMicrosecond
    Date value;

    @Override
    public Date value() {
      return this.value;
    }
  }

}
