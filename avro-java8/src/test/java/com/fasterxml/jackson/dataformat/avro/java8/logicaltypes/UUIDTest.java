package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroDecimal;
import com.fasterxml.jackson.dataformat.avro.AvroUUID;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public class UUIDTest extends LogicalTypeTestCase<UUIDTest.UUIDTestCase> {
  static final UUID VALUE = UUID.randomUUID();

  @Override
  protected Class<UUIDTestCase> dataClass() {
    return UUIDTestCase.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.STRING;
  }

  @Override
  protected String logicalType() {
    return "uuid";
  }

  @Override
  protected UUIDTestCase testData() {
    UUIDTestCase v = new UUIDTestCase();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return VALUE.toString();
  }

  static class UUIDTestCase extends TestData<UUID> {
    @JsonProperty(required = true)
    @AvroUUID
    public UUID value;

    @Override
    public UUID value() {
      return this.value;
    }
  }
}
