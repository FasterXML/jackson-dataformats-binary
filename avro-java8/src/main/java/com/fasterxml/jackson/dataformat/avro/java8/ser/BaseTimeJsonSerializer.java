package com.fasterxml.jackson.dataformat.avro.java8.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public abstract class BaseTimeJsonSerializer<T> extends JsonSerializer<T> {
  final TimeUnit resolution;
  final ZoneId zoneId = ZoneId.of("UTC");

  BaseTimeJsonSerializer(TimeUnit resolution) {
    this.resolution = resolution;
  }

  abstract Instant toInstant(T input);

  @Override
  public void serialize(T input, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    final Instant instant = toInstant(input);
    final long output;
    switch (this.resolution) {
      case MICROSECONDS:
        output = ChronoUnit.MICROS.between(Instant.EPOCH, instant);
        break;
      case MILLISECONDS:
        output = instant.toEpochMilli();
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("%s is not supported", this.resolution)
        );
    }
    jsonGenerator.writeNumber(output);
  }
}
