package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.databind.JsonDeserializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class OffsetDateTimeDeserializer extends BaseTimeJsonDeserializer<OffsetDateTime> {
  public static JsonDeserializer<OffsetDateTime> MILLIS = new OffsetDateTimeDeserializer(TimeUnit.MILLISECONDS);
  public static JsonDeserializer<OffsetDateTime> MICROS = new OffsetDateTimeDeserializer(TimeUnit.MICROSECONDS);

  OffsetDateTimeDeserializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  protected OffsetDateTime fromInstant(Instant input) {
    return OffsetDateTime.ofInstant(input, this.zoneId);
  }
}
