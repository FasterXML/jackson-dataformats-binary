package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.LocalDate;

public class DateLocalDateTest extends LogicalTypeTestCase<DateLocalDateTest.TestCase> {
  static final LocalDate VALUE = LocalDate.of(2011, 3, 14);

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
    return "date";
  }

  @Override
  protected TestCase testData() {
    TestCase v = new TestCase();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return VALUE.toEpochDay();
  }

  @Override
  protected void configure(AvroMapper mapper) {

  }

  static class TestCase extends TestData<LocalDate> {
    @JsonProperty(required = true)
    @AvroType(schemaType = Schema.Type.INT, logicalType = AvroType.LogicalType.DATE)
    LocalDate value;

    @Override
    public LocalDate value() {
      return this.value;
    }
  }

}
