package com.fasterxml.jackson.dataformat.ion.jsr310;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.json.PackageVersion;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * A module that installs a collection of serializers and deserializers for java.time classes.
 */
public class IonJavaTimeModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public IonJavaTimeModule() {
        super(PackageVersion.VERSION);
        addSerializer(Instant.class, IonTimestampInstantSerializer.INSTANT);
        addSerializer(OffsetDateTime.class, IonTimestampInstantSerializer.OFFSET_DATE_TIME);
        addSerializer(ZonedDateTime.class, IonTimestampInstantSerializer.ZONED_DATE_TIME);

        addDeserializer(Instant.class, IonTimestampInstantDeserializer.INSTANT);
        addDeserializer(OffsetDateTime.class, IonTimestampInstantDeserializer.OFFSET_DATE_TIME);
        addDeserializer(ZonedDateTime.class, IonTimestampInstantDeserializer.ZONED_DATE_TIME);
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
