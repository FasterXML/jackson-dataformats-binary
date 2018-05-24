package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.codehaus.jackson.JsonGenerationException;

import java.io.IOException;
import java.math.BigDecimal;

public class AvroFixedDecimalSerializer extends JsonSerializer<BigDecimal> {
  final int scale;
  final int fixedSize;

  public AvroFixedDecimalSerializer(int scale, int fixedSize) {
    this.scale = scale;
    this.fixedSize = fixedSize;
  }

  @Override
  public void serialize(BigDecimal value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    if (scale != value.scale()) {
      throw new JsonGenerationException(
          String.format("Cannot encode decimal with scale %s as scale %s.", value.scale(), scale)
      );
    }
    byte fillByte = (byte)(value.signum() < 0 ? 255 : 0);
    byte[] unscaled = value.unscaledValue().toByteArray();
    byte[] bytes = new byte[this.fixedSize];
    int offset = bytes.length - unscaled.length;

    for(int i = 0; i < bytes.length; ++i) {
      if (i < offset) {
        bytes[i] = fillByte;
      } else {
        bytes[i] = unscaled[i - offset];
      }
    }

    jsonGenerator.writeBinary(bytes);
  }
}
