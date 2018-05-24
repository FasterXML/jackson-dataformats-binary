package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class AvroDateTimeSerializer extends JsonSerializer<Date> {
  public final static JsonSerializer<Date> MILLIS = new AvroDateTimeSerializer(TimeUnit.MILLISECONDS);
  public final static JsonSerializer<Date> MICROS = new AvroDateTimeSerializer(TimeUnit.MICROSECONDS);
  private final static Date MIN_DATE = new Date(0L);
  private final static Date MAX_DATE = new Date(
      TimeUnit.SECONDS.toMillis(86400)
  );

  private final TimeUnit resolution;
  private final long max;


  AvroDateTimeSerializer(TimeUnit resolution) {
    this.resolution = resolution;
    this.max = this.resolution.convert(86400, TimeUnit.SECONDS);
  }

  @Override
  public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    final long input = date.getTime();

    if (input < 0 || input > this.max) {
      throw new IllegalStateException(
          String.format("Value must be between %s and %s.", MIN_DATE, MAX_DATE)
      );
    }
    final long output = this.resolution.convert(input, TimeUnit.MILLISECONDS);
    jsonGenerator.writeNumber(output);
  }
}
