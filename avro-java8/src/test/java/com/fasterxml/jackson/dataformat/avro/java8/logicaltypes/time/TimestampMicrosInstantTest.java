package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimestampMicrosInstantTest extends LogicalTypeTestCase<TimestampMicrosInstantTest.TestCase> {
  static final Instant VALUE = new Date(1526955327123L).toInstant();

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

  @Override
  protected TestCase testData() {
    TestCase v = new TestCase();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return ChronoUnit.MICROS.between(Instant.EPOCH, VALUE);
  }

  static class TestCase extends TestData<Instant> {
    @JsonProperty(required = true)
    @AvroType(schemaType = Schema.Type.LONG, logicalType = AvroType.LogicalType.TIMESTAMP_MICROSECOND)
    Instant value;

    @Override
    public Instant value() {
      return this.value;
    }
  }

}
