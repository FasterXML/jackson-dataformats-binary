package com.fasterxml.jackson.dataformat.avro.jsr310;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.json.PackageVersion;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.avro.jsr310.deser.AvroInstantDeserializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.deser.AvroLocalDateDeserializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.deser.AvroLocalDateTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.deser.AvroLocalTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.ser.AvroInstantSerializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.ser.AvroLocalDateSerializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.ser.AvroLocalDateTimeSerializer;
import com.fasterxml.jackson.dataformat.avro.jsr310.ser.AvroLocalTimeSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * A module that installs a collection of serializers and deserializers for java.time classes.
 */
public class AvroJavaTimeModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public AvroJavaTimeModule() {
        super(PackageVersion.VERSION);

        addSerializer(Instant.class, AvroInstantSerializer.INSTANT);
        addSerializer(OffsetDateTime.class, AvroInstantSerializer.OFFSET_DATE_TIME);
        addSerializer(ZonedDateTime.class, AvroInstantSerializer.ZONED_DATE_TIME);
        addSerializer(LocalDateTime.class, AvroLocalDateTimeSerializer.INSTANCE);
        addSerializer(LocalDate.class, AvroLocalDateSerializer.INSTANCE);
        addSerializer(LocalTime.class, AvroLocalTimeSerializer.INSTANCE);

        addDeserializer(Instant.class, AvroInstantDeserializer.INSTANT);
        addDeserializer(OffsetDateTime.class, AvroInstantDeserializer.OFFSET_DATE_TIME);
        addDeserializer(ZonedDateTime.class, AvroInstantDeserializer.ZONED_DATE_TIME);
        addDeserializer(LocalDateTime.class, AvroLocalDateTimeDeserializer.INSTANCE);
        addDeserializer(LocalDate.class, AvroLocalDateDeserializer.INSTANCE);
        addDeserializer(LocalTime.class, AvroLocalTimeDeserializer.INSTANCE);
    }

    @Override
    public String getModuleName() {
        return getClass().getName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
    }
}
