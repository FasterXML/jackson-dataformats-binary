package com.fasterxml.jackson.dataformat.avro.java8.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

public class LocalTimeSerializer extends JsonSerializer<LocalTime> {
  public static final JsonSerializer<LocalTime> MILLIS = new LocalTimeSerializer(TimeUnit.MILLISECONDS);
  public static final JsonSerializer<LocalTime> MICROS = new LocalTimeSerializer(TimeUnit.MICROSECONDS);

  private final TimeUnit resolution;

  LocalTimeSerializer(TimeUnit resolution) {
    this.resolution = resolution;
  }

  @Override
  public void serialize(LocalTime localTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    switch (this.resolution) {
      case MICROSECONDS:
        long micros = TimeUnit.NANOSECONDS.toMicros(localTime.toNanoOfDay());
        jsonGenerator.writeNumber(micros);
        break;
      case MILLISECONDS:
        int millis = (int)TimeUnit.NANOSECONDS.toMillis(localTime.toNanoOfDay());
        jsonGenerator.writeNumber(millis);
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("%s is not supported", this.resolution)
        );
    }
  }
}
