package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

public class TimestampMillisecondSerializers {
  public static final JsonSerializer<LocalDateTime> LOCAL_DATE_TIME = new LocalDateTimeSerializer();
  public static final JsonSerializer<Date> DATE = new DateSerializer();
  public static final JsonSerializer<ZonedDateTime> ZONED_DATE_TIME = new ZonedDateTimeSerializer();
  public static final JsonSerializer<OffsetDateTime> OFFSET_DATE_TIME = new OffsetDateTimeSerializer();

  static class DateSerializer extends JsonSerializer<Date> {
    @Override
    public void serialize(Date d, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeNumber(d.getTime());
    }
  }

  static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime d, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeNumber(
          d.toInstant(ZoneOffset.UTC).toEpochMilli()
      );
    }
  }

  static class ZonedDateTimeSerializer extends JsonSerializer<ZonedDateTime> {
    @Override
    public void serialize(ZonedDateTime d, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeNumber(
          d.toInstant().toEpochMilli()
      );
    }
  }

  static class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
    @Override
    public void serialize(OffsetDateTime d, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeNumber(
          d.toInstant().toEpochMilli()
      );
    }
  }
}
