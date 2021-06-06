package com.fasterxml.jackson.dataformat.avro.jsr310;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroJavaTimeModule_serialization_and_deserializationTest {

    private static AvroMapper newAvroMapper() {
        return AvroMapper.builder()
                .addModules(new AvroJavaTimeModule())
                .build();
    }

    @Test
    public void testWithInstant() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        mapper.acceptJsonFormatVisitor(Instant.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();

        Instant expectedInstant = Instant.ofEpochMilli(0L);

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(expectedInstant);
        Instant deserInstant = mapper.readerFor(Instant.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserInstant).isEqualTo(expectedInstant);
    }

    @Test
    public void testWithOffsetDateTime() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        mapper.acceptJsonFormatVisitor(OffsetDateTime.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();

        OffsetDateTime expectedOffsetDateTime = OffsetDateTime.of(2021, 6, 6, 12, 00, 30, 00, ZoneOffset.ofHours(2));

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(expectedOffsetDateTime);
        OffsetDateTime deserOffsetDateTime = mapper.readerFor(OffsetDateTime.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserOffsetDateTime.toInstant()).isEqualTo(expectedOffsetDateTime.toInstant());
    }

    @Test
    public void testWithZonedDateTime() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        mapper.acceptJsonFormatVisitor(ZonedDateTime.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();

        ZonedDateTime expectedZonedDateTime = ZonedDateTime.of(2021, 6, 6, 12, 00, 30, 00, ZoneOffset.ofHours(2));

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(expectedZonedDateTime);
        ZonedDateTime deserZonedDateTime = mapper.readerFor(ZonedDateTime.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserZonedDateTime.toInstant()).isEqualTo(expectedZonedDateTime.toInstant());
    }

}
