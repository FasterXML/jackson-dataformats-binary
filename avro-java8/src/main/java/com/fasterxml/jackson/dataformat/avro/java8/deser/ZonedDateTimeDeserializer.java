package com.fasterxml.jackson.dataformat.avro.java8.deser;

import com.fasterxml.jackson.databind.JsonDeserializer;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class ZonedDateTimeDeserializer extends BaseTimeJsonDeserializer<ZonedDateTime> {
  public static JsonDeserializer<ZonedDateTime> MILLIS = new ZonedDateTimeDeserializer(TimeUnit.MILLISECONDS);
  public static JsonDeserializer<ZonedDateTime> MICROS = new ZonedDateTimeDeserializer(TimeUnit.MICROSECONDS);

  ZonedDateTimeDeserializer(TimeUnit resolution) {
    super(resolution);
  }

  @Override
  protected ZonedDateTime fromInstant(Instant input) {
    return ZonedDateTime.ofInstant(input, this.zoneId);
  }
}
