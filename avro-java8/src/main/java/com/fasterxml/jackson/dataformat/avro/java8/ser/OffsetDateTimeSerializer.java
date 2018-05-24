package com.fasterxml.jackson.dataformat.avro.java8.ser;

import com.fasterxml.jackson.databind.JsonSerializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class OffsetDateTimeSerializer extends BaseTimeJsonSerializer<OffsetDateTime> {
  public static final JsonSerializer<OffsetDateTime> MILLIS = new OffsetDateTimeSerializer(TimeUnit.MILLISECONDS);
  public static final JsonSerializer<OffsetDateTime> MICROS = new OffsetDateTimeSerializer(TimeUnit.MICROSECONDS);

  OffsetDateTimeSerializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  Instant toInstant(OffsetDateTime input) {
    return input.toInstant();
  }
}
