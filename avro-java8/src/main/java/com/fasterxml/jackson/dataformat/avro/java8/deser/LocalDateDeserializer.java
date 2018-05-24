package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;

public class LocalDateDeserializer extends JsonDeserializer<LocalDate> {
  public static final JsonDeserializer<LocalDate> INSTANCE = new LocalDateDeserializer();

  @Override
  public LocalDate deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    return LocalDate.ofEpochDay(jsonParser.getLongValue());
  }
}
