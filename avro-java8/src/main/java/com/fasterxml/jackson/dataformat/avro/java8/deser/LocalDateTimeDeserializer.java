package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.databind.JsonDeserializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class LocalDateTimeDeserializer extends BaseTimeJsonDeserializer<LocalDateTime> {
  public static JsonDeserializer<LocalDateTime> MILLIS = new LocalDateTimeDeserializer(TimeUnit.MILLISECONDS);
  public static JsonDeserializer<LocalDateTime> MICROS = new LocalDateTimeDeserializer(TimeUnit.MICROSECONDS);

  LocalDateTimeDeserializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  protected LocalDateTime fromInstant(Instant input) {
    return LocalDateTime.ofInstant(input, this.zoneId);
  }
}
