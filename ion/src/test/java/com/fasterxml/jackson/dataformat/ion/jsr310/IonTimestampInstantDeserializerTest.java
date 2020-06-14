package com.fasterxml.jackson.dataformat.ion.jsr310;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

import org.junit.Test;

import com.amazon.ion.Timestamp;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

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
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat02() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 183917322);
        Instant actual = READER.readValue("123456789.183917322");
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat03() throws Exception {
        Instant expected = Instant.now();
        Instant actual = READER.readValue(TimestampUtils.getFractionalSeconds(expected).toString());
        assertEquals("The value is not correct.", expected, actual);
    }
    
    /**
     * Test the upper-bound of Instant.
     */
    @Test
    public void testDeserializationAsFloatEdgeCase01() throws Exception {
        String input = Instant.MAX.getEpochSecond() + ".999999999";
        Instant actual = READER.readValue(input);
        assertEquals(actual, Instant.MAX);
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
        assertEquals(actual, Instant.MIN);
        assertEquals(Instant.MIN.getEpochSecond(), actual.getEpochSecond());
        assertEquals(0, actual.getNano());
    }

    @Test(expected = DateTimeException.class)
    public void testDeserializationAsFloatEdgeCase03() throws Exception {
        // Instant can't go this low
        String input = Instant.MIN.getEpochSecond() + ".1";
        READER.readValue(input);
    }

    @Test(expected = DateTimeException.class)
    public void testDeserializationAsFloatEdgeCase04() throws Exception {
        // 1s beyond the upper-bound of Instant.
        String input = (Instant.MAX.getEpochSecond() + 1) + ".0";
        READER.readValue(input);
    }

    @Test(expected = DateTimeException.class)
    public void testDeserializationAsFloatEdgeCase05() throws Exception {
        // 1s beyond the lower-bound of Instant.
        String input = (Instant.MIN.getEpochSecond() - 1) + ".0";
        READER.readValue(input);
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
    @Test(timeout = 100)
    public void testDeserializationAsFloatEdgeCase08() throws Exception {
        Instant actual = READER.readValue("1e308");
        assertEquals(0, actual.getEpochSecond());
    }

    @Test(timeout = 100)
    public void testDeserializationAsFloatEdgeCase09() throws Exception {
        Instant actual = READER.readValue("-1e308");
        assertEquals(0, actual.getEpochSecond());
    }

    /**
     * Same for large negative exponents.
     */
    @Test(timeout = 100)
    public void testDeserializationAsFloatEdgeCase10() throws Exception {
        Instant actual = READER.readValue("1e-323");
        assertEquals(0, actual.getEpochSecond());
    }

    @Test(timeout = 100)
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

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt02Nanoseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L);
        Instant actual = READER
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt03Nanoseconds() throws Exception {
        Instant expected = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant actual = READER
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(expected.getEpochSecond()));

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt01Milliseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(0L);
        Instant actual = READER
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt02Milliseconds() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 422000000);
        Instant actual = READER
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789422");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt03Milliseconds() throws Exception {
        Instant expected = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant actual = READER
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(expected.toEpochMilli()));

        assertEquals("The value is not correct.", expected, actual);
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
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp02() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 183917322);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.UTC);
        Instant actual = READER.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp03() throws Exception {
        Instant expected = Instant.now();
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.UTC);
        Instant actual = READER.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
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
        assertTrue("The actual should be an Instant.", actual instanceof Instant);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo02() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 0);
        IonObjectMapper m = newMapperBuilder()
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + Instant.class.getName() + "\",123456789]", Temporal.class);
        assertTrue("The actual should be an Instant.", actual instanceof Instant);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo03() throws Exception {
        Instant expected = Instant.ofEpochSecond(123456789L, 422000000);
        IonObjectMapper m = newMapperBuilder()
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + Instant.class.getName() + "\", 123456789422]", Temporal.class);
        assertTrue("The actual should be an Instant.", actual instanceof Instant);
        assertEquals("The value is not correct.", expected, actual);
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

        assertTrue("The actual should be an Instant.", actual instanceof Instant);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationFromStringWithNonZeroOffset01() throws Exception {
        Instant expected = Instant.now();
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.ofHours(8));
        Instant result = READER.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, result);
    }

    @Test
    public void testDeserializationFromStringWithNonZeroOffset02() throws Exception {
        Instant expected = Instant.now();
        Timestamp timestamp = TimestampUtils.toTimestamp(expected, ZoneOffset.ofHours(-8));
        Instant result = READER.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, result);
    }
}
