package tools.jackson.dataformat.ion.jsr310;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.concurrent.TimeUnit;

import com.amazon.ion.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class IonTimestampInstantDeserializerTest {

    private static final IonObjectMapper MAPPER = IonObjectMapper.builder()
            .addModule(new IonJavaTimeModule())
            .build();

    private static final ObjectReader READER = MAPPER.readerFor(Instant.class);

    private IonObjectMapper.Builder newMapperBuilder() {
        return IonObjectMapper.builder()
                .addModule(new IonJavaTimeModule());
    }

    /*
     **********************************************************************
     * Deserialization from decimal actual (seconds with fractions)
     **********************************************************************
     */

    @Test
    public void testDeserializationAsFloat01() throws Exception {
        Instant expected = Instant.ofEpochSecond(0L);
        Instant actual = READER.readValue("0.000000");
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsFloat02() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 183917322);
        Instant actual = READER.readValue("123456789.183917322");
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsFloat03() throws Exception {
        Instant expected = Instant.now();
        Instant actual = READER.readValue(TimestampUtils.getFractionalSeconds(expected).toString());
        assertEquals(expected, actual, "The value is not correct.");
    }

    /**
     * Test the upper-bound of Instant.
     */
    @Test
    public void testDeserializationAsFloatEdgeCase01() throws Exception {
        String input = Instant.MAX.getEpochSecond() + ".999999999";
        Instant actual = READER.readValue(input);
        assertEquals(Instant.MAX, actual);
        assertEquals(Instant.MAX.getEpochSecond(), actual.getEpochSecond());
        assertEquals(999999999, actual.getNano());
    }

    /**
     * Test the lower-bound of Instant.
     */
    @Test
    public void testDeserializationAsFloatEdgeCase02() throws Exception {
        String input = Instant.MIN.getEpochSecond() + ".0";
        Instant actual = READER.readValue(input);
        assertEquals(Instant.MIN, actual);
        assertEquals(Instant.MIN.getEpochSecond(), actual.getEpochSecond());
        assertEquals(0, actual.getNano());
    }

    @Test
    public void testDeserializationAsFloatEdgeCase03() throws Exception {
        assertThrows(DateTimeException.class, () -> {
            // Instant can't go this low
            String input = Instant.MIN.getEpochSecond() + ".1";
            READER.readValue(input);
        });
    }

    @Test
    public void testDeserializationAsFloatEdgeCase04() throws Exception {
        assertThrows(DateTimeException.class, () -> {
            // 1s beyond the upper-bound of Instant.
            String input = (Instant.MAX.getEpochSecond() + 1) + ".0";
            READER.readValue(input);
        });
    }

    @Test
    public void testDeserializationAsFloatEdgeCase05() throws Exception {
        assertThrows(DateTimeException.class, () -> {
            // 1s beyond the lower-bound of Instant.
            String input = (Instant.MIN.getEpochSecond() - 1) + ".0";
            READER.readValue(input);
        });
    }

    @Test
    public void testDeserializationAsFloatEdgeCase06() throws Exception {
        // Into the positive zone where everything becomes zero.
        Instant actual = READER.readValue("1e64");
        assertEquals(0, actual.getEpochSecond());
    }

    @Test
    public void testDeserializationAsFloatEdgeCase07() throws Exception {
        // Into the negative zone where everything becomes zero.
        Instant actual = READER.readValue("-1e64");
        assertEquals(0, actual.getEpochSecond());
    }

    /**
     * Numbers with very large exponents can take a long time, but still result in zero.
     * https://github.com/FasterXML/jackson-databind/issues/2141
     */
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @Test
    public void testDeserializationAsFloatEdgeCase08() throws Exception {
        Instant actual = READER.readValue("1e308");
        assertEquals(0, actual.getEpochSecond());
    }

    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @Test
    public void testDeserializationAsFloatEdgeCase09() throws Exception {
        Instant actual = READER.readValue("-1e308");
        assertEquals(0, actual.getEpochSecond());
    }

    /**
     * Same for large negative exponents.
     */
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @Test
    public void testDeserializationAsFloatEdgeCase10() throws Exception {
        Instant actual = READER.readValue("1e-323");
        assertEquals(0, actual.getEpochSecond());
    }

    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @Test
    public void testDeserializationAsFloatEdgeCase11() throws Exception {
        Instant actual = READER.readValue("-1e-323");
        assertEquals(0, actual.getEpochSecond());
    }

    /*
     **********************************************************************
     * Deserialization from int actual (milliseconds)
     **********************************************************************
     */

    @Test
    public void testDeserializationAsInt01Nanoseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(0L);
        Instant actual = READER
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsInt02Nanoseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L);
        Instant actual = READER
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789");

        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsInt03Nanoseconds() throws Exception {
        Instant expected = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant actual = READER
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(expected.getEpochSecond()));

        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsInt01Milliseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(0L);
        Instant actual = READER
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsInt02Milliseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 422000000);
        Instant actual = READER
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789422");

        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsInt03Milliseconds() throws Exception {
        Instant expected = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant actual = READER
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(expected.toEpochMilli()));

        assertEquals(expected, actual, "The value is not correct.");
    }

    /*
     **********************************************************************
     * Deserialization from Ion timestamp actual
     **********************************************************************
     */

    @Test
    public void testDeserializationAsIonTimestamp01() throws Exception {
        Instant expected = Instant.ofEpochSecond(0L);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.UTC);
        Instant actual = READER.readValue(timestamp.toString());
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsIonTimestamp02() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 183917322);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.UTC);
        Instant actual = READER.readValue(timestamp.toString());
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationAsIonTimestamp03() throws Exception {
        Instant expected = Instant.now();
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.UTC);
        Instant actual = READER.readValue(timestamp.toString());
        assertEquals(expected, actual, "The value is not correct.");
    }

    /*
     **********************************************************************
     * Deserialization of actuals with type info
     **********************************************************************
     */

    @Test
    public void testDeserializationWithTypeInfo01() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 183917322);
        IonObjectMapper m = newMapperBuilder()
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + Instant.class.getName() + "\",123456789.183917322]", Temporal.class);
        assertInstanceOf(Instant.class, actual, "The actual should be an Instant.");
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationWithTypeInfo02() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 0);
        IonObjectMapper m = newMapperBuilder()
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + Instant.class.getName() + "\",123456789]", Temporal.class);
        assertInstanceOf(Instant.class, actual, "The actual should be an Instant.");
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationWithTypeInfo03() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 422000000);
        IonObjectMapper m = newMapperBuilder()
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + Instant.class.getName() + "\", 123456789422]", Temporal.class);
        assertInstanceOf(Instant.class, actual, "The actual should be an Instant.");
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationWithTypeInfo04() throws Exception {
        Instant expected = Instant.now();
        IonObjectMapper m = newMapperBuilder()
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.UTC);
        Temporal actual = m.readValue("[\"" + Instant.class.getName() + "\"," + timestamp.toString() + "]",
                Temporal.class);

        assertInstanceOf(Instant.class, actual, "The actual should be an Instant.");
        assertEquals(expected, actual, "The value is not correct.");
    }

    @Test
    public void testDeserializationFromStringWithNonZeroOffset01() throws Exception {
        Instant expected = Instant.now();
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.ofHours(8));
        Instant result = READER.readValue(timestamp.toString());
        assertEquals(expected, result, "The value is not correct.");
    }

    @Test
    public void testDeserializationFromStringWithNonZeroOffset02() throws Exception {
        Instant expected = Instant.now();
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.ofHours(-8));
        Instant result = READER.readValue(timestamp.toString());
        assertEquals(expected, result, "The value is not correct.");
    }
}
