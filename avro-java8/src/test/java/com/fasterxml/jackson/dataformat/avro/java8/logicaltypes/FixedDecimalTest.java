package com.fasterxml.jackson.dataformat.avro.java8.logicaltypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;

import java.math.BigDecimal;

public class FixedDecimalTest extends LogicalTypeTestCase<FixedDecimalTest.FixedDecimal> {
  static final BigDecimal VALUE = BigDecimal.valueOf(123456, 3);

  @Override
  protected Class<FixedDecimal> dataClass() {
    return FixedDecimal.class;
  }

  @Override
  protected Schema.Type schemaType() {
    return Schema.Type.FIXED;
  }

  @Override
  protected String logicalType() {
    return "decimal";
  }

  @Override
  protected FixedDecimal testData() {
    FixedDecimal v = new FixedDecimal();
    v.value = VALUE;
    return v;
  }

  @Override
  protected Object convertedValue() {
    return new Conversions.DecimalConversion().toFixed(VALUE, this.schema, this.schema.getLogicalType());
  }

  static class FixedDecimal extends TestData<BigDecimal> {
    @JsonProperty(required = true)
    @AvroType(precision = 3, scale = 3, fixedSize = 8, typeNamespace = "com.foo.example", typeName = "Decimal", schemaType = Schema.Type.FIXED, logicalType = AvroType.LogicalType.DECIMAL)
    public BigDecimal value;

    @Override
    public BigDecimal value() {
      return this.value;
    }
  }

}
