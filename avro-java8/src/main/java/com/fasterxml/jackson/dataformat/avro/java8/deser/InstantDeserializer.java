package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.databind.JsonDeserializer;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class InstantDeserializer extends BaseTimeJsonDeserializer<Instant> {
  public static JsonDeserializer<Instant> MILLIS = new InstantDeserializer(TimeUnit.MILLISECONDS);
  public static JsonDeserializer<Instant> MICROS = new InstantDeserializer(TimeUnit.MICROSECONDS);

  InstantDeserializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  Instant fromInstant(Instant input) {
    return input;
  }
}
