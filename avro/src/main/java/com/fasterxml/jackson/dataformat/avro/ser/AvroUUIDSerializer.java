package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.UUID;

public class AvroUUIDSerializer extends JsonSerializer<UUID> {
  public static final JsonSerializer<UUID> INSTANCE = new AvroUUIDSerializer();

  @Override
  public void serialize(UUID uuid, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeString(uuid.toString());
  }
}
