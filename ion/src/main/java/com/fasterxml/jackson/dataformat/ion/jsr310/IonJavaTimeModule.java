package com.fasterxml.jackson.dataformat.ion.jsr310;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.JacksonModule;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;

import com.fasterxml.jackson.dataformat.ion.PackageVersion;

/**
 * A module that installs a collection of serializers and deserializers for java.time classes.
 *<p>
 * As of Jackson 3.0 does not extend {@code SimpleModule} to keep it {@link java.io.Serializable}
 * (value serializers, deserializers are not serializable in 3.0).
 */
public class IonJavaTimeModule extends JacksonModule
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    public IonJavaTimeModule() { }

    @Override
    public void setupModule(SetupContext context)
    {
        context.addDeserializers(new SimpleDeserializers()
                .addDeserializer(Instant.class, IonTimestampInstantDeserializer.INSTANT)
                .addDeserializer(OffsetDateTime.class, IonTimestampInstantDeserializer.OFFSET_DATE_TIME)
                .addDeserializer(ZonedDateTime.class, IonTimestampInstantDeserializer.ZONED_DATE_TIME)
        );
        context.addSerializers(new SimpleSerializers()
                .addSerializer(Instant.class, IonTimestampInstantSerializer.INSTANT)
                .addSerializer(OffsetDateTime.class, IonTimestampInstantSerializer.OFFSET_DATE_TIME)
                .addSerializer(ZonedDateTime.class, IonTimestampInstantSerializer.ZONED_DATE_TIME)
        );
    }

    @Override
    public String getModuleName() {
        return getClass().getName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }
}
