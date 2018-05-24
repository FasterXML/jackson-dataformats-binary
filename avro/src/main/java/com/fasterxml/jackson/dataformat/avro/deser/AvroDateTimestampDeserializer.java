package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public class AvroDateTimestampDeserializer extends JsonDeserializer<Date> {
  public static final JsonDeserializer<Date> MILLIS = new AvroDateTimestampDeserializer(TimeUnit.MILLISECONDS);
  public static final JsonDeserializer<Date> MICROS = new AvroDateTimestampDeserializer(TimeUnit.MICROSECONDS);
  private final TimeUnit resolution;

  AvroDateTimestampDeserializer(TimeUnit resolution) {
    this.resolution = resolution;
  }

  @Override
  public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
    final long input = jsonParser.getLongValue();
    final long output;
    switch (this.resolution) {
      case MICROSECONDS:
        output = TimeUnit.MICROSECONDS.toMillis(input);
        break;
      case MILLISECONDS:
        output = input;
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("%s is not supported", this.resolution)
        );
    }
    return new Date(output);
  }
}
