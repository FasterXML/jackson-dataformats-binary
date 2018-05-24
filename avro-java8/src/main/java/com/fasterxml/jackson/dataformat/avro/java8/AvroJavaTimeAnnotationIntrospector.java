package com.fasterxml.jackson.dataformat.avro.java8;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.dataformat.avro.AvroDate;
import com.fasterxml.jackson.dataformat.avro.AvroTimeMicrosecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimeMillisecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMicrosecond;
import com.fasterxml.jackson.dataformat.avro.AvroTimestampMillisecond;
import com.fasterxml.jackson.dataformat.avro.java8.deser.LocalDateDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.OffsetDateTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.deser.ZonedDateTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.LocalDateSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.LocalTimeSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.OffsetDateTimeSerializer;
import com.fasterxml.jackson.dataformat.avro.java8.ser.ZonedDateTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

class AvroJavaTimeAnnotationIntrospector extends AnnotationIntrospector {
  static final AvroJavaTimeAnnotationIntrospector INSTANCE = new AvroJavaTimeAnnotationIntrospector();

  @Override
  public Object findSerializer(Annotated a) {
    AvroTimestampMillisecond timestampMillisecond = _findAnnotation(a, AvroTimestampMillisecond.class);
    if (null != timestampMillisecond) {
      if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
        return LocalDateTimeSerializer.MILLIS;
      }
      if (a.getRawType().isAssignableFrom(OffsetDateTime.class)) {
        return OffsetDateTimeSerializer.MILLIS;
      }
      if (a.getRawType().isAssignableFrom(ZonedDateTime.class)) {
        return ZonedDateTimeSerializer.MILLIS;
      }
    }

    AvroTimestampMicrosecond timestampMicrosecond = _findAnnotation(a, AvroTimestampMicrosecond.class);
    if (null != timestampMicrosecond) {
      if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
        return LocalDateTimeSerializer.MICROS;
      }
      if (a.getRawType().isAssignableFrom(OffsetDateTime.class)) {
        return OffsetDateTimeSerializer.MICROS;
      }
      if (a.getRawType().isAssignableFrom(ZonedDateTime.class)) {
        return ZonedDateTimeSerializer.MICROS;
      }
    }

    AvroDate date = _findAnnotation(a, AvroDate.class);
    if (null != date) {
      if (a.getRawType().isAssignableFrom(LocalDate.class)) {
        return LocalDateSerializer.INSTANCE;
      }
    }

    AvroTimeMillisecond timeMillisecond = _findAnnotation(a, AvroTimeMillisecond.class);
    if (null != timeMillisecond) {
      if (a.getRawType().isAssignableFrom(LocalTime.class)) {
        return LocalTimeSerializer.MILLIS;
      }
    }

    AvroTimeMicrosecond timeMicrosecond = _findAnnotation(a, AvroTimeMicrosecond.class);
    if (null != timeMicrosecond) {
      if (a.getRawType().isAssignableFrom(LocalTime.class)) {
        return LocalTimeSerializer.MICROS;
      }
    }

    return super.findSerializer(a);

  }

  @Override
  public Object findDeserializer(Annotated a) {
    AvroTimestampMillisecond timestampMillisecond = _findAnnotation(a, AvroTimestampMillisecond.class);
    if (null != timestampMillisecond) {
      if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
        return LocalDateTimeDeserializer.MILLIS;
      }
      if (a.getRawType().isAssignableFrom(OffsetDateTime.class)) {
        return OffsetDateTimeDeserializer.MILLIS;
      }
      if (a.getRawType().isAssignableFrom(ZonedDateTime.class)) {
        return ZonedDateTimeDeserializer.MILLIS;
      }
    }

    AvroTimestampMicrosecond timestampMicrosecond = _findAnnotation(a, AvroTimestampMicrosecond.class);
    if (null != timestampMicrosecond) {
      if (a.getRawType().isAssignableFrom(LocalDateTime.class)) {
        return LocalDateTimeDeserializer.MICROS;
      }
      if (a.getRawType().isAssignableFrom(OffsetDateTime.class)) {
        return OffsetDateTimeDeserializer.MICROS;
      }
      if (a.getRawType().isAssignableFrom(ZonedDateTime.class)) {
        return ZonedDateTimeDeserializer.MICROS;
      }
    }

    AvroDate date = _findAnnotation(a, AvroDate.class);
    if (null != date) {
      if (a.getRawType().isAssignableFrom(LocalDate.class)) {
        return LocalDateDeserializer.INSTANCE;
      }
    }

    AvroTimeMillisecond timeMillisecond = _findAnnotation(a, AvroTimeMillisecond.class);
    if (null != timeMillisecond) {
      if (a.getRawType().isAssignableFrom(LocalTime.class)) {
        return LocalTimeDeserializer.MILLIS;
      }
    }

    AvroTimeMicrosecond timeMicrosecond = _findAnnotation(a, AvroTimeMicrosecond.class);
    if (null != timeMicrosecond) {
      if (a.getRawType().isAssignableFrom(LocalTime.class)) {
        return LocalTimeDeserializer.MICROS;
      }
    }

    return super.findDeserializer(a);
  }

  @Override
  public Version version() {
    return PackageVersion.VERSION;
  }
}
