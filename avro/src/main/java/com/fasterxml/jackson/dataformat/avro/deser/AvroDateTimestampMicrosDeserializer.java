package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Date;

public class AvroDateTimestampMicrosDeserializer extends JsonDeserializer<Date> {
  public static JsonDeserializer<Date> INSTANCE = new AvroDateTimestampMicrosDeserializer();

  @Override
  public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    long value = jsonParser.getLongValue();

    if (value == 0L) {
      return new Date(value);
    }

    return new Date(value / 1000L);
  }
}
