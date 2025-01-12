package com.fasterxml.jackson.dataformat.ion.jsr310;

import java.time.*;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.ion.PackageVersion;

/**
 * A module that installs a collection of serializers and deserializers for java.time classes.
 */
public class IonJavaTimeModule extends SimpleModule
{
    private static final long serialVersionUID = 1L;

    public IonJavaTimeModule() {
        super(IonJavaTimeModule.class.getName(), PackageVersion.VERSION);
        addSerializer(Instant.class, IonTimestampInstantSerializer.INSTANT);
        addSerializer(OffsetDateTime.class, IonTimestampInstantSerializer.OFFSET_DATE_TIME);
        addSerializer(ZonedDateTime.class, IonTimestampInstantSerializer.ZONED_DATE_TIME);

        addDeserializer(Instant.class, IonTimestampInstantDeserializer.INSTANT);
        addDeserializer(OffsetDateTime.class, IonTimestampInstantDeserializer.OFFSET_DATE_TIME);
        addDeserializer(ZonedDateTime.class, IonTimestampInstantDeserializer.ZONED_DATE_TIME);
    }
}
