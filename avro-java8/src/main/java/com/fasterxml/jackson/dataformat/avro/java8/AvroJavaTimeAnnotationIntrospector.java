package com.fasterxml.jackson.dataformat.avro.java8;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.dataformat.avro.AvroAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.avro.AvroType;
import com.fasterxml.jackson.dataformat.avro.java8.deser.InstantDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.LocalDateDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.InstantSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.LocalDateSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.LocalTimeSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

class AvroJavaTimeAnnotationIntrospector extends AvroAnnotationIntrospector {
  static final AvroJavaTimeAnnotationIntrospector INSTANCE = new AvroJavaTimeAnnotationIntrospector();

  @Override
  public Object findSerializer(Annotated a) {
    AvroType logicalType = _findAnnotation(a, AvroType.class);
    if (null != logicalType) {
      switch (logicalType.logicalType()) {
        case TIMESTAMP_MILLISECOND:
          if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
            return LocalDateTimeSerializer.MILLIS;
          }
          if (a.getRawType().isAssignableFrom(Instant.class)) {
            return InstantSerializer.MILLIS;
          }
          break;
        case TIMESTAMP_MICROSECOND:
          if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
            return LocalDateTimeSerializer.MICROS;
          }
          if (a.getRawType().isAssignableFrom(Instant.class)) {
            return InstantSerializer.MICROS;
          }
          break;
        case DATE:
          if (a.getRawType().isAssignableFrom(LocalDate.class)) {
            return LocalDateSerializer.INSTANCE;
          }
          break;
        case TIME_MILLISECOND:
          if (a.getRawType().isAssignableFrom(LocalTime.class)) {
            return LocalTimeSerializer.MILLIS;
          }
          break;
        case TIME_MICROSECOND:
          if (a.getRawType().isAssignableFrom(LocalTime.class)) {
            return LocalTimeSerializer.MICROS;
          }
          break;
      }
    }

    return super.findSerializer(a);
  }

  @Override
  public Object findDeserializer(Annotated a) {
    AvroType logicalType = _findAnnotation(a, AvroType.class);
    if (null != logicalType) {
      switch (logicalType.logicalType()) {
        case TIMESTAMP_MILLISECOND:
          if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
            return LocalDateTimeDeserializer.MILLIS;
          }
          if (a.getRawType().isAssignableFrom(Instant.class)) {
            return InstantDeserializer.MILLIS;
          }
          break;
        case TIMESTAMP_MICROSECOND:
          if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
            return LocalDateTimeDeserializer.MICROS;
          }
          if (a.getRawType().isAssignableFrom(Instant.class)) {
            return InstantDeserializer.MICROS;
          }
          break;
        case DATE:
          if (a.getRawType().isAssignableFrom(LocalDate.class)) {
            return LocalDateDeserializer.INSTANCE;
          }
          break;
        case TIME_MILLISECOND:
          if (a.getRawType().isAssignableFrom(LocalTime.class)) {
            return LocalTimeDeserializer.MILLIS;
          }
          break;
        case TIME_MICROSECOND:
          if (a.getRawType().isAssignableFrom(LocalTime.class)) {
            return LocalTimeDeserializer.MICROS;
          }
          break;
      }
    }

    return super.findDeserializer(a);
  }

  @Override
  public Version version() {
    return PackageVersion.VERSION;
  }
}
