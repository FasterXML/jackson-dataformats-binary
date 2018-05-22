package com.fasterxml.jackson.dataformat.avro.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMicroTimeModule;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMillisecond;
import com.fasterxml.jackson.dataformat.avro.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class TimeMillisDateTest extends LogicalTypeTestCase<TimeMillisDateTest.TestCase> {
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
    return "timestamp-millis";
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
    return 1526955327123L;
  }

  @Override
  protected void configure(AvroMapper mapper) {

  }

  static class TestCase extends TestData<Date> {
    @JsonProperty(required = true)
    @AvroTimestampMillisecond
    Date value;

    @Override
    public Date value() {
      return this.value;
    }
  }

}
