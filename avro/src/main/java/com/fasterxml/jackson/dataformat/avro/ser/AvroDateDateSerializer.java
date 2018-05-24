package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class AvroDateDateSerializer extends JsonSerializer<Date> {
  public static final JsonSerializer<Date> INSTANCE = new AvroDateDateSerializer();
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
  private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

  @Override
  public void serialize(Date value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    Calendar calendar = Calendar.getInstance(UTC);
    calendar.setTime(value);
    if (calendar.get(Calendar.HOUR_OF_DAY) != 0 || calendar.get(Calendar.MINUTE) != 0 ||
        calendar.get(Calendar.SECOND) != 0 || calendar.get(Calendar.MILLISECOND) != 0) {
      throw new IllegalStateException("Date type should not have any time fields set to non-zero values.");
    }
    long unixMillis = calendar.getTimeInMillis();
    int output =(int) (unixMillis / MILLIS_PER_DAY);
    jsonGenerator.writeNumber(output);
  }
}
