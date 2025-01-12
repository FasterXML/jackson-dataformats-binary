package tools.jackson.dataformat.ion.jsr310;

import java.time.*;
import java.time.temporal.Temporal;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IonTimestampZonedDateTimeSerializerTest {

    private static final ZoneId Z1 = ZoneId.of("America/Chicago");
    private static final ZoneId Z2 = ZoneId.of("America/Anchorage");
    private static final ZoneId Z3 = ZoneId.of("America/Los_Angeles");

    private IonObjectMapper.Builder newMapperBuilder() {
        return IonObjectMapper.builder()
                .addModule(new IonJavaTimeModule());
    }

    @Test
    public void testSerializationAsTimestamp01Nanoseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        String value = mapper.writeValueAsString(date);
        assertEquals("0.", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp01Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        String value = mapper.writeValueAsString(date);
        assertEquals("0", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp02Nanoseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z2);
        String value = mapper.writeValueAsString(date);
        assertEquals("123456789.183917322", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp02Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z2);
        String value = mapper.writeValueAsString(date);
        assertEquals("123456789183", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp03Nanoseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        ZonedDateTime date = ZonedDateTime.now(Z3);
        String value = mapper.writeValueAsString(date);
        assertEquals(TimestampUtils.getFractionalSeconds(date.toInstant()).toString(), value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp03Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        ZonedDateTime date = ZonedDateTime.now(Z3);
        String value = mapper.writeValueAsString(date);
        assertEquals(Long.toString(date.toInstant().toEpochMilli()), value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationAsString01() throws Exception {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        String value = mapper.writeValueAsString(date);
        assertEquals(TimestampUtils.toTimestamp(date.toInstant(), date.getOffset()).toString(), value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationAsString02() throws Exception {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z2);
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        String value = mapper.writeValueAsString(date);
        assertEquals(TimestampUtils.toTimestamp(date.toInstant(), date.getOffset()).toString(), value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationAsString03() throws Exception {
        ZonedDateTime date = ZonedDateTime.now(Z3);
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        String value = mapper.writeValueAsString(date);
        assertEquals(TimestampUtils.toTimestamp(date.toInstant(), date.getOffset()).toString(), value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationWithTypeInfo01() throws Exception {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z2);
        IonObjectMapper mapper = newMapperBuilder()
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        String value = mapper.writeValueAsString(date);
        assertEquals("'" + ZonedDateTime.class.getName() + "'::123456789.183917322", value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationWithTypeInfo02() throws Exception {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z2);
        IonObjectMapper mapper = newMapperBuilder()
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        String value = mapper.writeValueAsString(date);
        assertEquals("'" + ZonedDateTime.class.getName() + "'::123456789183", value,
                "The value is not correct.");
    }

    @Test
    public void testSerializationWithTypeInfo03() throws Exception {
        ZonedDateTime date = ZonedDateTime.now(Z3);
        IonObjectMapper mapper = newMapperBuilder()
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        String value = mapper.writeValueAsString(date);
        assertNotNull(value, "The value should not be null.");
        assertEquals("'" + ZonedDateTime.class.getName() + "'::"
                + TimestampUtils.toTimestamp(date.toInstant(), date.getOffset()).toString(), value,
                "The value is not correct.");
    }
}

