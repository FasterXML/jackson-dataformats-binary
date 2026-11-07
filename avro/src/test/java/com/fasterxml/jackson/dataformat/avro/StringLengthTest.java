package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;
import com.fasterxml.jackson.dataformat.avro.testsupport.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

// Same as [dataformats-binary#819] but for `string` values and Map keys: declared
// length exceeding available content should fail on end-of-input, without first
// allocating a buffer for the full declared length
public class StringLengthTest extends AvroTestBase
{
    static class StringWrapper {
        public String s;

        protected StringWrapper() { }
        public StringWrapper(String s) { this.s = s; }
    }

    static class MapWrapper {
        public Map<String, Integer> m;

        protected MapWrapper() { }
        public MapWrapper(Map<String, Integer> m) { this.m = m; }
    }

    private final static String STRING_SCHEMA_JSON = aposToQuotes("{'type':'record','name':'StringWrapper',"
            +"'fields':[{'name':'s','type':'string'}]}");

    private final static String MAP_SCHEMA_JSON = aposToQuotes("{'type':'record','name':'MapWrapper',"
            +"'fields':[{'name':'m','type':{'type':'map','values':'int'}}]}");

    private final AvroMapper JACKSON_MAPPER = AvroMapper.builder(new AvroFactory()).build();
    private final AvroMapper APACHE_MAPPER = AvroMapper.builder(new ApacheAvroFactory()).build();

    // Lower `maxStringLength` used for constraint tests
    private final static int MAX_STRING_LENGTH = 1000;

    // Longer than `AvroParserImpl.LONGEST_NON_CHUNKED_BINARY_READ`, not a multiple of it
    private final static int LONG_LENGTH = 777_777;

    // Longest length Apache Avro decoder itself accepts for a `string`
    private final static int HUGE_LENGTH = Integer.MAX_VALUE - 8;

    @Test
    public void testTruncatedString() throws Exception
    {
        for (int len : new int[] { HUGE_LENGTH, LONG_LENGTH, 10 }) {
            byte[] doc = _concat(_lengthPrefix(len), new byte[3]);
            _testTruncated(STRING_SCHEMA_JSON, StringWrapper.class, doc);
        }
    }

    @Test
    public void testTruncatedMapKey() throws Exception
    {
        for (int len : new int[] { HUGE_LENGTH, LONG_LENGTH, 10 }) {
            // one-entry block, then the key
            byte[] doc = _concat(_lengthPrefix(1), _concat(_lengthPrefix(len), new byte[3]));
            _testTruncated(MAP_SCHEMA_JSON, MapWrapper.class, doc);
        }
    }

    // String length constraint must be enforced by both backends, for `string`
    // values and Map keys; and for Apache backend, both up front (declared length
    // long enough to exceed limit whatever the content) and after decoding
    @Test
    public void testStringLengthLimit() throws Exception
    {
        for (AvroMapper mapper : _mappers(MAX_STRING_LENGTH)) {
            final AvroSchema stringSchema = mapper.schemaFrom(STRING_SCHEMA_JSON);
            final AvroSchema mapSchema = mapper.schemaFrom(MAP_SCHEMA_JSON);
            final ObjectReader stringReader = mapper.readerFor(StringWrapper.class).with(stringSchema);
            final ObjectReader mapReader = mapper.readerFor(MapWrapper.class).with(mapSchema);

            // At the limit: fine, including multi-byte content longer (in bytes) than limit
            for (String ok : new String[] { _repeat("a", MAX_STRING_LENGTH),
                    _repeat("\u20AC", MAX_STRING_LENGTH) }) {
                byte[] doc = mapper.writer(stringSchema).writeValueAsBytes(new StringWrapper(ok));
                assertEquals(ok, ((StringWrapper) stringReader.readValue(doc)).s);
                doc = mapper.writer(mapSchema).writeValueAsBytes(
                        new MapWrapper(Collections.singletonMap(ok, 1)));
                assertEquals(Collections.singletonMap(ok, 1), ((MapWrapper) mapReader.readValue(doc)).m);
            }

            // Over the limit: one char over (only detected once decoded), then
            // long enough to be rejected based on declared length
            for (int len : new int[] { MAX_STRING_LENGTH + 1, 5 * MAX_STRING_LENGTH }) {
                final String tooLong = _repeat("a", len);
                byte[] doc = mapper.writer(stringSchema).writeValueAsBytes(new StringWrapper(tooLong));
                _verifyTooLong(stringReader, doc);
                doc = mapper.writer(mapSchema).writeValueAsBytes(
                        new MapWrapper(Collections.singletonMap(tooLong, 1)));
                _verifyTooLong(mapReader, doc);
            }
        }
    }

    private void _verifyTooLong(ObjectReader r, byte[] doc) throws Exception
    {
        for (Object input : new Object[] { doc, _stream(doc) }) {
            // (databind wraps `StreamConstraintsException` for Map values)
            JsonProcessingException e = assertThrows(JsonProcessingException.class,
                    () -> {
                        if (input instanceof byte[]) {
                            r.readValue((byte[]) input);
                        } else {
                            r.readValue((InputStream) input);
                        }
                    });
            verifyException(e, "String value length");
            verifyException(e, "exceeds the maximum allowed ("+MAX_STRING_LENGTH);
        }
    }

    @Test
    public void testNegativeStringLength() throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { JACKSON_MAPPER, APACHE_MAPPER }) {
            final ObjectReader r = mapper.readerFor(StringWrapper.class)
                    .with(mapper.schemaFrom(STRING_SCHEMA_JSON));
            try {
                r.readValue(_lengthPrefix(-1));
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid length indicator for String: -1");
            }
        }
    }

    @Test
    public void testLongValidString() throws Exception
    {
        final String value = _longString();
        for (AvroMapper mapper : new AvroMapper[] { JACKSON_MAPPER, APACHE_MAPPER }) {
            final AvroSchema schema = mapper.schemaFrom(STRING_SCHEMA_JSON);
            byte[] doc = mapper.writer(schema).writeValueAsBytes(new StringWrapper(value));
            final ObjectReader r = mapper.readerFor(StringWrapper.class).with(schema);
            assertEquals(value, ((StringWrapper) r.readValue(doc)).s);
            assertEquals(value, ((StringWrapper) r.readValue(
                    ThrottledInputStream.wrap(new ByteArrayInputStream(doc), 1000))).s);
            assertEquals("", ((StringWrapper) r.readValue(
                    mapper.writer(schema).writeValueAsBytes(new StringWrapper("")))).s);
        }
    }

    @Test
    public void testLongValidMapKey() throws Exception
    {
        final Map<String, Integer> value = new LinkedHashMap<>();
        value.put("a", 1);
        value.put(_longString(), 2);
        value.put("", 3);
        for (AvroMapper mapper : new AvroMapper[] { JACKSON_MAPPER, APACHE_MAPPER }) {
            final AvroSchema schema = mapper.schemaFrom(MAP_SCHEMA_JSON);
            byte[] doc = mapper.writer(schema).writeValueAsBytes(new MapWrapper(value));
            final ObjectReader r = mapper.readerFor(MapWrapper.class).with(schema);
            assertEquals(value, ((MapWrapper) r.readValue(doc)).m);
            assertEquals(value, ((MapWrapper) r.readValue(
                    ThrottledInputStream.wrap(new ByteArrayInputStream(doc), 1000))).m);
        }
    }

    // Mix of 1-, 2-, 3- and 4-byte UTF-8 characters, so some cross chunk boundaries
    private static String _longString() {
        final String[] parts = { "abc", "é", "€", "😀" };
        StringBuilder sb = new StringBuilder(LONG_LENGTH + 4);
        for (int i = 0; sb.length() < LONG_LENGTH; ++i) {
            sb.append(parts[i % parts.length]);
        }
        return sb.toString();
    }

    private void _testTruncated(String schemaJson, Class<?> type, byte[] doc) throws Exception
    {
        // Lift string length limit, so that huge declared lengths fail on
        // end-of-input, not on (Apache backend's up-front) length check
        for (AvroMapper mapper : _mappers(Integer.MAX_VALUE)) {
            final ObjectReader r = mapper.readerFor(type).with(mapper.schemaFrom(schemaJson));
            _verifyTruncated(r, doc);
            _verifyTruncated(r, _stream(doc));
        }
    }

    private void _verifyTruncated(ObjectReader r, Object input) throws Exception
    {
        try {
            if (input instanceof byte[]) {
                r.readValue((byte[]) input);
            } else {
                r.readValue((InputStream) input);
            }
            fail("Should not pass");
        } catch (EOFException e) {
            // Apache decoder reports end-of-input as-is
        } catch (JsonProcessingException e) {
            verifyException(e, "end-of-input");
        }
    }

    private static AvroMapper[] _mappers(int maxStringLength) {
        final StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxStringLength(maxStringLength).build();
        AvroFactory jacksonF = new AvroFactory();
        jacksonF.setStreamReadConstraints(constraints);
        AvroFactory apacheF = new ApacheAvroFactory();
        apacheF.setStreamReadConstraints(constraints);
        return new AvroMapper[] { AvroMapper.builder(jacksonF).build(),
                AvroMapper.builder(apacheF).build() };
    }

    private static String _repeat(String str, int count) {
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; ++i) {
            sb.append(str);
        }
        return sb.toString();
    }

    private static InputStream _stream(byte[] doc) {
        return ThrottledInputStream.wrap(new ByteArrayInputStream(doc), 7);
    }

    // Avro `long`/`int` encoding: zig-zag, then variable-length
    private static byte[] _lengthPrefix(int len) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long v = ((long) len << 1) ^ (len >> 31);
        while ((v & ~0x7FL) != 0L) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
        return out.toByteArray();
    }

    private static byte[] _concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
