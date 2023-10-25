package com.fasterxml.jackson.dataformat.avro.jsr310;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroJavaTimeModule_serialization_and_deserializationTest {

    static final String SCHEMA_LONG_AND_TIMESTAMP_MILLIS = "{" +
            " \"type\": \"long\"," +
            " \"logicalType\": \"timestamp-millis\"" +
            "}";

    static final String SCHEMA_LONG_AND_LOCAL_TIMESTAMP_MILLIS = "{" +
            " \"type\": \"long\"," +
            " \"logicalType\": \"local-timestamp-millis\"" +
            "}";

    static final String SCHEMA_INT_AND_TIME_MILLIS = "{" +
            " \"type\": \"int\"," +
            " \"logicalType\": \"time-millis\"" +
            "}";

    static final String SCHEMA_INT_AND_DATE = "{" +
            " \"type\": \"int\"," +
            " \"logicalType\": \"date\"" +
            "}";

    private static AvroMapper newAvroMapper() {
        return AvroMapper.builder()
                .addModule(new AvroJavaTimeModule())
                .build();
    }

    @Test
    public void testWithInstant_millis() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchema schema = mapper.schemaFrom(SCHEMA_LONG_AND_TIMESTAMP_MILLIS);

        Instant serializedInstant = Instant.ofEpochSecond(930303030, 333_222_111);
        Instant expectedInstant = Instant.ofEpochSecond(930303030, 333_000_000);

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(serializedInstant);
        Instant deserInstant = mapper.readerFor(Instant.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserInstant).isEqualTo(expectedInstant);
    }

    @Test
    public void testWithOffsetDateTime_millis() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchema schema = mapper.schemaFrom(SCHEMA_LONG_AND_TIMESTAMP_MILLIS);

        OffsetDateTime serializedOffsetDateTime = OffsetDateTime.of(2021, 6, 6, 12, 0, 30, 333_222_111, ZoneOffset.ofHours(2));
        OffsetDateTime expectedOffsetDateTime = OffsetDateTime.of(2021, 6, 6, 12, 0, 30, 333_000_000, ZoneOffset.ofHours(2));

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(serializedOffsetDateTime);
        OffsetDateTime deserOffsetDateTime = mapper.readerFor(OffsetDateTime.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserOffsetDateTime.toInstant()).isEqualTo(expectedOffsetDateTime.toInstant());
    }

    @Test
    public void testWithZonedDateTime_millis() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchema schema = mapper.schemaFrom(SCHEMA_LONG_AND_TIMESTAMP_MILLIS);

        ZonedDateTime serializedZonedDateTime = ZonedDateTime.of(2021, 6, 6, 12, 0, 30, 333_222_111, ZoneOffset.ofHours(2));
        ZonedDateTime expectedZonedDateTime = ZonedDateTime.of(2021, 6, 6, 12, 0, 30, 333_000_000, ZoneOffset.ofHours(2));

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(serializedZonedDateTime);
        ZonedDateTime deserZonedDateTime = mapper.readerFor(ZonedDateTime.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserZonedDateTime.toInstant()).isEqualTo(expectedZonedDateTime.toInstant());
    }

    @Test
    public void testWithLocalDateTime_millis() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchema schema = mapper.schemaFrom(SCHEMA_LONG_AND_LOCAL_TIMESTAMP_MILLIS);

        LocalDateTime serializedLocalDateTime = LocalDateTime.of(2021, 6, 6, 12, 0, 30, 333_222_111);
        LocalDateTime expectedLocalDateTime = LocalDateTime.of(2021, 6, 6, 12, 0, 30, 333_000_000);

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(serializedLocalDateTime);
        LocalDateTime deserLocalDateTime = mapper.readerFor(LocalDateTime.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserLocalDateTime).isEqualTo(expectedLocalDateTime);
    }

    @Test
    public void testWithLocalDate() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchema schema = mapper.schemaFrom(SCHEMA_INT_AND_DATE);

        LocalDate expectedLocalDate = LocalDate.of(2021, 6, 7);

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(expectedLocalDate);
        LocalDate deserLocalDate = mapper.readerFor(LocalDate.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserLocalDate).isEqualTo(expectedLocalDate);
    }

    @Test
    public void testWithLocalTime_millis() throws IOException {
        // GIVEN
        AvroMapper mapper = newAvroMapper();
        AvroSchema schema = mapper.schemaFrom(SCHEMA_INT_AND_TIME_MILLIS);

        LocalTime serializedLocalTime = LocalTime.of(23, 6, 6, 333_222_111);
        LocalTime expectedLocalTime = LocalTime.of(23, 6, 6, 333_000_000);

        // WHEN
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(serializedLocalTime);
        LocalTime deserLocalTime = mapper.readerFor(LocalTime.class).with(schema).readValue(serialized);

        // THEN
        assertThat(deserLocalTime).isEqualTo(expectedLocalTime);
    }

}
