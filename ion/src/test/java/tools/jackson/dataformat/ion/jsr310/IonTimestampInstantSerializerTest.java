package tools.jackson.dataformat.ion.jsr310;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

import com.amazon.ion.IonDecimal;
import com.amazon.ion.IonInt;
import com.amazon.ion.IonTimestamp;
import com.amazon.ion.Timestamp;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.ion.IonObjectMapper;

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
        assertNotNull("The value should not be null.", value);
        assertEquals("The value is not correct.", "0.", value);
    }

    @Test
    public void testSerializationAsTimestamp01Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.ofEpochSecond(0L);
        String value = mapper.writeValueAsString(date);
        assertEquals("The value is not correct.", "0", value);
    }

    @Test
    public void testSerializationAsTimestamp02Nanoseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        String value = mapper.writeValueAsString(date);
        assertEquals("The value is not correct.", "123456789.183917322", value);
    }

    @Test
    public void testSerializationAsTimestamp02Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        String value = mapper.writeValueAsString(date);
        assertEquals("The value is not correct.", "123456789183", value);
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
        assertEquals("The value is not correct.", TimestampUtils.getFractionalSeconds(date).toString(), value);
    }

    @Test
    public void testSerializationAsTimestamp03Milliseconds() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();

        Instant date = Instant.now();
        String value = mapper.writeValueAsString(date);
        assertEquals("The value is not correct.", Long.toString(date.toEpochMilli()), value);
    }

    @Test
    public void testSerializationAsString01() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        Instant date = Instant.ofEpochSecond(0L);
        Timestamp value = ((IonTimestamp)mapper.writeValueAsIonValue(date)).timestampValue();
        assertEquals("The value is not correct.", TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value);
    }

    @Test
    public void testSerializationAsString02() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        Instant date = Instant.ofEpochSecond(123456789L, 183917322);
        Timestamp value = ((IonTimestamp)mapper.writeValueAsIonValue(date)).timestampValue();
        assertEquals("The value is not correct.", TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value);
    }

    @Test
    public void testSerializationAsString03() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        Instant date = Instant.now();
        Timestamp value = ((IonTimestamp)mapper.writeValueAsIonValue(date)).timestampValue();
        assertEquals("The value is not correct.", TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value);
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
        assertEquals("The value is not correct.", new BigDecimal("123456789.183917322"), value.bigDecimalValue());
        assertEquals("The does does not contain the expected number of annotations.", 1, value.getTypeAnnotations().length);
        assertEquals("The does does not contain the expected annotation.", Instant.class.getName(), value.getTypeAnnotations()[0]);
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
        assertEquals("The value is not correct.", 123456789183L, value.longValue());
        assertEquals("The does does not contain the expected number of annotations.", 1, value.getTypeAnnotations().length);
        assertEquals("The does does not contain the expected annotation.", Instant.class.getName(), value.getTypeAnnotations()[0]);
    }

    @Test
    public void testSerializationWithTypeInfo03() throws Exception {
        IonObjectMapper mapper = newMapperBuilder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addMixIn(Instant.class, MockObjectConfiguration.class)
                .build();

        Instant date = Instant.now();
        IonTimestamp value = (IonTimestamp) mapper.writeValueAsIonValue(date);
        assertEquals("The value is not correct.", TimestampUtils.toTimestamp(date, ZoneOffset.UTC), value.timestampValue());
        assertEquals("The does does not contain the expected number of annotations.", 1, value.getTypeAnnotations().length);
        assertEquals("The does does not contain the expected annotation.", Instant.class.getName(), value.getTypeAnnotations()[0]);
    }
}
