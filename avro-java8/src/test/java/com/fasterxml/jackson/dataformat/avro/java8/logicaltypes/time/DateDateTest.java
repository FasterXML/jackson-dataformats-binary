package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.time;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.LogicalTypeTestCase;
import com.fasterxml.jackson.dataformat.avro.java8.logicaltypes.TestData;
import org.apache.avro.Schema;

import java.time.LocalDate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DateDateTest extends LogicalTypeTestCase<DateDateTest.TestCase> {
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
    v.value = new Date(TimeUnit.DAYS.toMillis(VALUE.toEpochDay()));
    return v;
  }

  @Override
  protected Object convertedValue() {
    return VALUE.toEpochDay();
  }

  @Override
  protected void configure(AvroMapper mapper) {

  }

  static class TestCase extends TestData<Date> {
    @JsonProperty(required = true)
    @AvroType(schemaType = Schema.Type.INT, logicalType = AvroType.LogicalType.DATE)
    Date value;

    @Override
    public Date value() {
      return this.value;
    }
  }

}
