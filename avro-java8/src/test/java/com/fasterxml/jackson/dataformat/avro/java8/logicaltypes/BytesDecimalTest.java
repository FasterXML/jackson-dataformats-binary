package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;

import java.math.BigDecimal;

public class BytesDecimalTest extends LogicalTypeTestCase<BytesDecimalTest.BytesDecimal> {
  static final BigDecimal VALUE = BigDecimal.valueOf(123456, 3);

  @Override
  protected Class<BytesDecimal> dataClass() {
    return BytesDecimal.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.BYTES;
  }

  @Override
  protected String logicalType() {
    return "decimal";
  }

  @Override
  protected BytesDecimal testData() {
    BytesDecimal v = new BytesDecimal();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return new Conversions.DecimalConversion().toBytes(VALUE, this.schema, this.schema.getLogicalType());
  }

  static class BytesDecimal extends TestData<BigDecimal> {
    @JsonProperty(required = true)
    @AvroType(precision = 3, scale = 3, schemaType = Schema.Type.BYTES, logicalType = AvroType.LogicalType.DECIMAL)
    public BigDecimal value;

    @Override
    public BigDecimal value() {
      return this.value;
    }
  }
}
