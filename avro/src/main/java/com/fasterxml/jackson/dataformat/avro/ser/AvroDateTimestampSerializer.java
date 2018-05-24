package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class AvroDateTimestampSerializer extends JsonSerializer<Date> {
  public final static JsonSerializer<Date> MILLIS = new AvroDateTimestampSerializer(TimeUnit.MILLISECONDS);
  public final static JsonSerializer<Date> MICROS = new AvroDateTimestampSerializer(TimeUnit.MICROSECONDS);

  private final TimeUnit resolution;

  AvroDateTimestampSerializer(TimeUnit resolution) {
    this.resolution = resolution;
  }

  @Override
  public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    final long input = date.getTime();
    final long output;
    switch (this.resolution) {
      case MICROSECONDS:
        output = TimeUnit.MILLISECONDS.toMicros(input);
        break;
      case MILLISECONDS:
        output = input;
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("%s is not supported", this.resolution)
        );
    }
    jsonGenerator.writeNumber(output);
  }
}
