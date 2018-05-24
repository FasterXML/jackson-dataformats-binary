package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class AvroDecimalDeserializer extends JsonDeserializer<BigDecimal> {
  private final int scale;

  public AvroDecimalDeserializer(int scale) {
    this.scale = scale;
  }

  @Override
  public BigDecimal deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    final byte[] bytes = jsonParser.getBinaryValue();
    return new BigDecimal(new BigInteger(bytes), this.scale);
  }
}
