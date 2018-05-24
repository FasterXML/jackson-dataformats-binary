package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.codehaus.jackson.JsonGenerationException;

import java.io.IOException;
import java.math.BigDecimal;

public class AvroBytesDecimalSerializer extends JsonSerializer<BigDecimal> {
  final int scale;

  public AvroBytesDecimalSerializer(int scale) {
    this.scale = scale;
  }

  @Override
  public void serialize(BigDecimal decimal, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    if (scale != decimal.scale()) {
      throw new JsonGenerationException(
          String.format("Cannot encode decimal with scale %s as scale %s.", decimal.scale(), scale)
      );
    }
    jsonGenerator.writeBinary(decimal.unscaledValue().toByteArray());
  }
}
