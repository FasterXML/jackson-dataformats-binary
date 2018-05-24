package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;

public class AvroDateTimestampMicrosSerializer extends JsonSerializer<Date> {
  public static JsonSerializer<Date> INSTANCE = new AvroDateTimestampMicrosSerializer();

  @Override
  public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeNumber(date.getTime() * 1000L);
  }
}
