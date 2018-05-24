package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroTimeMillisecond;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

public class TimeMillisLocalTimeTest extends LogicalTypeTestCase<TimeMillisLocalTimeTest.TestCase> {
  @Override
  protected Class<TestCase> dataClass() {
    return TestCase.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.INT;
  }

  @Override
  protected String logicalType() {
    return "time-millis";
  }

  static final LocalTime VALUE = LocalTime.of(3, 3, 14);

  @Override
  protected TestCase testData() {
    TestCase v = new TestCase();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return TimeUnit.NANOSECONDS.toMillis(VALUE.toNanoOfDay());
  }

  @Override
  protected void configure(AvroMapper mapper) {

  }

  static class TestCase extends TestData<LocalTime> {
    @JsonProperty(required = true)
    @AvroTimeMillisecond
    LocalTime value;

    @Override
    public LocalTime value() {
      return this.value;
    }
  }

}
