package com.fasterxml.jackson.dataformat.avro.java8.ser;

import com.fasterxml.jackson.databind.JsonSerializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public class LocalDateTimeSerializer extends BaseTimeJsonSerializer<LocalDateTime> {
  public static final JsonSerializer<LocalDateTime> MILLIS = new LocalDateTimeSerializer(TimeUnit.MILLISECONDS);
  public static final JsonSerializer<LocalDateTime> MICROS = new LocalDateTimeSerializer(TimeUnit.MICROSECONDS);

  LocalDateTimeSerializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  Instant toInstant(LocalDateTime input) {
    return input.toInstant(ZoneOffset.UTC);
  }
}
