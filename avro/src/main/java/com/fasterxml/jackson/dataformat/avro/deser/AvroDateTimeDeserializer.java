package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public class AvroDateTimeDeserializer extends JsonDeserializer<Date> {
  public static final JsonDeserializer<Date> MILLIS = new AvroDateTimeDeserializer(TimeUnit.MILLISECONDS);
  public static final JsonDeserializer<Date> MICROS = new AvroDateTimeDeserializer(TimeUnit.MICROSECONDS);
  private final TimeUnit resolution;
  private final long max;

  AvroDateTimeDeserializer(TimeUnit resolution) {
    this.resolution = resolution;
    this.max = this.resolution.convert(86400, TimeUnit.SECONDS);
  }

  @Override
  public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    final long input = jsonParser.getLongValue();

    if (input < 0 || input > this.max) {
      throw new IllegalStateException(
          String.format("Value must be between 0 and %s %s(s).", this.max, this.resolution)
      );
    }
    final long output = TimeUnit.MILLISECONDS.convert(input, this.resolution);
    return new Date(output);
  }
}
