package tools.jackson.dataformat.ion.jsr310;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import com.amazon.ion.*;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IonTimestampInstantSerializerTest {

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

        Instant date = Instant.ofEpochSecond(0L);
        String value = mapper.writeValueAsString(date);
        assertNotNull(value, "The value should not be null.");
        assertEquals("0.", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp01Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.ofEpochSecond(0L);
        String value = mapper.writeValueAsString(date);
        assertEquals("0", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp02Nanoseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        String value = mapper.writeValueAsString(date);
        assertEquals("123456789.183917322", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp02Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        String value = mapper.writeValueAsString(date);
        assertEquals("123456789183", value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp03Nanoseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.now();
        String value = mapper.writeValueAsString(date);
        //TODO
        assertEquals(TimestampUtils.getFractionalSeconds(date).toString(), value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsTimestamp03Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.now();
        String value = mapper.writeValueAsString(date);
        assertEquals(Long.toString(date.toEpochMilli()), value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsString01() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        Instant date = Instant.ofEpochSecond(0L);
        Timestamp value = ((IonTimestamp)mapper.writeValueAsIonValue(date)).timestampValue();
        assertEquals(TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsString02() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        Timestamp value = ((IonTimestamp)mapper.writeValueAsIonValue(date)).timestampValue();
        assertEquals(TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value, "The value is not correct.");
    }

    @Test
    public void testSerializationAsString03() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        Instant date = Instant.now();
        Timestamp value = ((IonTimestamp)mapper.writeValueAsIonValue(date)).timestampValue();
        assertEquals(TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value, "The value is not correct.");
    }

    @Test
    public void testSerializationWithTypeInfo01() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Instant.class, MockObjectConfiguration.class)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        IonDecimal value = (IonDecimal) mapper.writeValueAsIonValue(date);
        assertEquals(new BigDecimal("123456789.183917322"), value.bigDecimalValue(), "The value is not correct.");
        assertEquals(1, value.getTypeAnnotations().length, "The does does not contain the expected number of annotations.");
        assertEquals(Instant.class.getName(), value.getTypeAnnotations()[0], "The does does not contain the expected annotation.");
    }

    @Test
    public void testSerializationWithTypeInfo02() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Instant.class, MockObjectConfiguration.class)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        IonInt value = (IonInt) mapper.writeValueAsIonValue(date);
        assertEquals(123456789183L, value.longValue(), "The value is not correct.");
        assertEquals(1, value.getTypeAnnotations().length, "The does does not contain the expected number of annotations.");
        assertEquals(Instant.class.getName(), value.getTypeAnnotations()[0], "The does does not contain the expected annotation.");
    }

    @Test
    public void testSerializationWithTypeInfo03() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addMixIn(Instant.class, MockObjectConfiguration.class)
                .build();

        Instant date = Instant.now();
        IonTimestamp value = (IonTimestamp) mapper.writeValueAsIonValue(date);
        assertEquals(TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value.timestampValue(), "The value is not correct.");
        assertEquals(1, value.getTypeAnnotations().length, "The does does not contain the expected number of annotations.");
        assertEquals(Instant.class.getName(), value.getTypeAnnotations()[0], "The does does not contain the expected annotation.");
    }
}
