package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

public class LocalTimeDeserializer extends JsonDeserializer<LocalTime> {
  public static JsonDeserializer<LocalTime> MILLIS = new LocalTimeDeserializer(TimeUnit.MILLISECONDS);
  public static JsonDeserializer<LocalTime> MICROS = new LocalTimeDeserializer(TimeUnit.MICROSECONDS);

  final TimeUnit resolution;

  LocalTimeDeserializer(TimeUnit resolution) {
    this.resolution = resolution;
  }

  @Override
  public LocalTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    long value = jsonParser.getLongValue();
    long nanos = this.resolution.toNanos(value);
    return LocalTime.ofNanoOfDay(nanos);
  }
}
