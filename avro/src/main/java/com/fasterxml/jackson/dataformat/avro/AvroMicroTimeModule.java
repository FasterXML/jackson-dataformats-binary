package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class AvroMicroTimeModule extends SimpleModule {
  public AvroMicroTimeModule() {
    super.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());

  }


  static class LocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
    protected LocalDateTimeSerializer() {
      super(LocalDateTime.class);
    }



    @Override
    public void serializeWithType(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
      super.serializeWithType(value, gen, serializers, typeSer);
    }

    @Override
    public void serialize(LocalDateTime localDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeNumber(
      localDateTime.toInstant(ZoneOffset.UTC).getNano() * 1000L
      );
    }
  }
}
