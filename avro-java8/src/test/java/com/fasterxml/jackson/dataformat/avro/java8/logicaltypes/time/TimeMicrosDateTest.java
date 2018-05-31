package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeMicrosDateTest extends LogicalTypeTestCase<TimeMicrosDateTest.TestCase> {
  static final Date VALUE = new Date(8127123L);

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

  @Override
  protected TestCase testData() {
    TestCase v = new TestCase();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    LocalTime time = VALUE.toInstant().atOffset(ZoneOffset.UTC).toLocalTime();
    return TimeUnit.NANOSECONDS.toMillis(time.toNanoOfDay());
  }

  @Override
  protected void configure(AvroMapper mapper) {

  }

  static class TestCase extends TestData<Date> {
    @JsonProperty(required = true)
    @AvroType(schemaType = Schema.Type.INT, logicalType = AvroType.LogicalType.TIME_MILLISECOND)
    Date value;

    @Override
    public Date value() {
      return this.value;
    }
  }

}
