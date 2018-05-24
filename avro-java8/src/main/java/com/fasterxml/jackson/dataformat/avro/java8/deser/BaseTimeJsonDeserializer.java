package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

public abstract class BaseTimeJsonDeserializer<T> extends JsonDeserializer<T> {
  final TimeUnit resolution;
  final ZoneId zoneId = ZoneId.of("UTC");

  BaseTimeJsonDeserializer(TimeUnit resolution) {
    this.resolution = resolution;
  }

  abstract T fromInstant(Instant input);

  @Override
  public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    final long input = p.getLongValue();
    final long output = this.resolution.convert(input, TimeUnit.MILLISECONDS);
    final Instant instant = Instant.ofEpochMilli(output);
    return fromInstant(instant);
  }
}
