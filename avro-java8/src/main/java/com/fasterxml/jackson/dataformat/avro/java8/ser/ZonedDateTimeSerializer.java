package com.fasterxml.jackson.dataformat.avro.java8.ser;

import com.fasterxml.jackson.databind.JsonSerializer;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class ZonedDateTimeSerializer extends BaseTimeJsonSerializer<ZonedDateTime> {
  public static final JsonSerializer<ZonedDateTime> MILLIS = new ZonedDateTimeSerializer(TimeUnit.MILLISECONDS);
  public static final JsonSerializer<ZonedDateTime> MICROS = new ZonedDateTimeSerializer(TimeUnit.MICROSECONDS);

  ZonedDateTimeSerializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  Instant toInstant(ZonedDateTime input) {
    return input.toInstant();
  }
}
