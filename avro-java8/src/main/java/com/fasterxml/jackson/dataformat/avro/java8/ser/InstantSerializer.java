package com.fasterxml.jackson.dataformat.avro.java8.ser;

import com.fasterxml.jackson.databind.JsonSerializer;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class InstantSerializer extends BaseTimeJsonSerializer<Instant> {
  public static final JsonSerializer<Instant> MILLIS = new InstantSerializer(TimeUnit.MILLISECONDS);
  public static final JsonSerializer<Instant> MICROS = new InstantSerializer(TimeUnit.MICROSECONDS);

  InstantSerializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  Instant toInstant(Instant input) {
    return input;
  }
}
