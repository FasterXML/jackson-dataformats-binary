package tools.jackson.dataformat.ion.jsr310;

import static java.time.ZoneOffset.UTC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.TimeZone;

import org.junit.Test;

import com.amazon.ion.Timestamp;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;

import tools.jackson.dataformat.ion.IonObjectMapper;

public class IonTimestampOffsetDateTimeDeserializerTest {

    private static final ZoneOffset Z1 = ZoneOffset.ofHours(-8);
    
    private static final ObjectReader READER_UTC_DEFAULT = newMapperBuilder()
            .defaultTimeZone(TimeZone.getTimeZone(UTC))
            .build()
            .readerFor(OffsetDateTime.class);

    private static final ObjectReader READER_Z1_DEFAULT = newMapperBuilder()
            .defaultTimeZone(TimeZone.getTimeZone(Z1))
            .build()
            .readerFor(OffsetDateTime.class);

    private static IonObjectMapper.Builder newMapperBuilder() {
        return IonObjectMapper.builder()
                .addModule(new IonJavaTimeModule()); 
    }

    /*
     **********************************************************************
     * Deserialization from decimal value (seconds with fractions)
     **********************************************************************
     */

    @Test
    public void testDeserializationAsFloat01() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue("0.000000");
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat01NonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT.readValue("0.000000");
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat02() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue("123456789.183917322");
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat02NonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT.readValue("123456789.183917322");
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat03() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(TimestampUtils.getFractionalSeconds(now).toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsFloat03NonUTCDefault() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT.readValue(TimestampUtils.getFractionalSeconds(now).toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    /*
     **********************************************************************
     * Deserialization from int value (milliseconds)
     **********************************************************************
     */

    @Test
    public void testDeserializationAsInt01Nanoseconds() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt01NanosecondsNonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt02Nanoseconds() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L), UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt02NanosecondsNonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L), Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt03Nanoseconds() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, UTC).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime actual = READER_UTC_DEFAULT
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(now.getEpochSecond()));

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt03NanosecondsNonUTCDefault() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, Z1).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime actual = READER_Z1_DEFAULT
                .with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(now.getEpochSecond()));

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt01Milliseconds() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt01MillisecondsNonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("0");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt02Milliseconds() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 422000000), UTC);
        OffsetDateTime actual = READER_UTC_DEFAULT
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789422");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt02MillisecondsNonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 422000000), Z1);
        OffsetDateTime actual = READER_Z1_DEFAULT
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue("123456789422");

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt03Milliseconds() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, UTC).truncatedTo(ChronoUnit.MILLIS);
        OffsetDateTime actual = READER_UTC_DEFAULT
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(now.toEpochMilli()));

        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsInt03MillisecondsNonUTCDefault() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, Z1).truncatedTo(ChronoUnit.MILLIS);
        OffsetDateTime actual = READER_Z1_DEFAULT
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue(Long.toString(now.toEpochMilli()));

        assertEquals("The value is not correct.", expected, actual);
    }

    /*
     **********************************************************************
     * Deserialization from Ion timestamp value
     **********************************************************************
     */

    @Test
    public void testDeserializationAsIonTimestamp01() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), UTC);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), expected.getOffset());
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp01NonUTCTimeOffset() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0L), Z1);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), expected.getOffset());
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp02() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), UTC);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), expected.getOffset());
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp02NonUTCTimeOffset() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z1);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), expected.getOffset());
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp03() throws Exception {
        OffsetDateTime expected = OffsetDateTime.now(UTC);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), expected.getOffset());
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp03NonUTCTimeOffset() throws Exception {
        OffsetDateTime expected = OffsetDateTime.now(Z1);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), expected.getOffset());
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp04UnknownOffset() throws Exception {
        OffsetDateTime expected = OffsetDateTime.now(UTC);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), null);
        OffsetDateTime actual = READER_UTC_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationAsIonTimestamp04UnknownOffsetNonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.now(Z1);
        Timestamp timestamp = TimestampUtils.toTimestamp(expected.toInstant(), null);
        OffsetDateTime actual = READER_Z1_DEFAULT.readValue(timestamp.toString());
        assertEquals("The value is not correct.", expected, actual);
    }

    /*
     **********************************************************************
     * Deserialization from values with type info
     **********************************************************************
     */

    @Test
    public void testDeserializationWithTypeInfo01() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), UTC);
        
        IonObjectMapper m = newMapperBuilder()
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\",123456789.183917322]", Temporal.class);
        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo01NonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 183917322), Z1);
        
        IonObjectMapper m = newMapperBuilder()
                .defaultTimeZone(TimeZone.getTimeZone(Z1))
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\",123456789.183917322]", Temporal.class);
        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo02() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 0), UTC);

        IonObjectMapper m = newMapperBuilder()
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\",123456789]", Temporal.class);
        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo02NonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 0), Z1);

        IonObjectMapper m = newMapperBuilder()
                .defaultTimeZone(TimeZone.getTimeZone(Z1))
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\",123456789]", Temporal.class);
        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo03() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 422000000), UTC);

        IonObjectMapper m = newMapperBuilder()
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\", 123456789422]", Temporal.class);
        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo03NonUTCDefault() throws Exception {
        OffsetDateTime expected = OffsetDateTime.ofInstant(Instant.ofEpochSecond(123456789L, 422000000), Z1);

        IonObjectMapper m = newMapperBuilder()
                .defaultTimeZone(TimeZone.getTimeZone(Z1))
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\", 123456789422]", Temporal.class);
        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo04() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, UTC);
        
        IonObjectMapper m = newMapperBuilder()
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Timestamp timestamp = TimestampUtils.toTimestamp(now, ZoneOffset.UTC);
        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\"," + timestamp.toString() + "]", 
                Temporal.class);

        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }

    @Test
    public void testDeserializationWithTypeInfo04NonUTCOffset() throws Exception {
        Instant now = Instant.now();
        OffsetDateTime expected = OffsetDateTime.ofInstant(now, Z1);
        
        IonObjectMapper m = newMapperBuilder()
                .addMixIn(Temporal.class, MockObjectConfiguration.class)
                .build();

        Timestamp timestamp = TimestampUtils.toTimestamp(now, expected.getOffset());
        Temporal actual = m.readValue("[\"" + OffsetDateTime.class.getName() + "\"," + timestamp.toString() + "]", 
                Temporal.class);

        assertTrue("The value should be an OffsetDateTime.", actual instanceof OffsetDateTime);
        assertEquals("The value is not correct.", expected, actual);
    }
}
