package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.UUID;

public class AvroUUIDDeserializer extends JsonDeserializer<UUID> {
  public static JsonDeserializer<UUID> INSTANCE = new AvroUUIDDeserializer();
  @Override
  public UUID deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    String value = jsonParser.getText();
    return UUID.fromString(value);
  }
}
