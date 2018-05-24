package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Date;


public class AvroDateDateDeserializer extends JsonDeserializer<Date> {
  public static final JsonDeserializer<Date> INSTANCE = new AvroDateDateDeserializer();
  private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

  AvroDateDateDeserializer() {

  }

  @Override
  public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    final long input = jsonParser.getLongValue();
    return new java.util.Date(input * MILLIS_PER_DAY);
  }
}
