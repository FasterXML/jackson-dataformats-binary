package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.java8.AvroJavaTimeModule;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public class TimestampMicrosLocalDateTimeTest extends LogicalTypeTestCase<TimestampMicrosLocalDateTimeTest.TestCase> {
  static final LocalDateTime VALUE = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(1526955327123L),
      ZoneId.of("UTC")
  );

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
    Instant instant = VALUE.toInstant(ZoneOffset.UTC);
    return (TimeUnit.SECONDS.toMicros(instant.getEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(instant.getNano()));
  }

  @Override
  protected void configure(AvroMapper mapper) {
    mapper.registerModule(new AvroJavaTimeModule());
  }

  static class TestCase extends TestData<LocalDateTime> {
    @JsonProperty(required = true)
    @AvroType(schemaType = Schema.Type.LONG, logicalType = AvroType.LogicalType.TIMESTAMP_MICROSECOND)
    LocalDateTime value;

    @Override
    public LocalDateTime value() {
      return this.value;
    }
  }

}
