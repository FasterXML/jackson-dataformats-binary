package tools.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.avro.testsupport.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

// Same as [dataformats-binary#819] but for `string` values and Map keys: declared
// length exceeding available content should fail on end-of-input, without first
// allocating a buffer for the full declared length. Plus [dataformats-binary#901]:
// `maxStringLength` constraint must be enforced by both backends.
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

    // Lower `maxStringLength` used for constraint tests
    private final static int MAX_STRING_LENGTH = 1000;

    // Longer than `AvroParserImpl.LONGEST_NON_CHUNKED_BINARY_READ`, not a multiple of it
    private final static int LONG_LENGTH = 777_777;

    // Longest length Apache Avro decoder itself accepts for a `string`
    private final static int HUGE_LENGTH = Integer.MAX_VALUE - 8;

    // Native and Apache backends, without string length limit: so that huge declared
    // lengths fail on end-of-input, not on (Apache backend's up-front) length check
    private final static AvroMapper[] MAPPERS = _mappers(Integer.MAX_VALUE);

    private final static AvroMapper[] LIMITED_MAPPERS = _mappers(MAX_STRING_LENGTH);

    @Test
    public void testTruncatedString() throws Exception
    {
        for (int len : new int[] { HUGE_LENGTH, LONG_LENGTH, 10 }) {
            byte[] doc = _concat(_lengthPrefix(len), new byte[3]);
            for (AvroMapper mapper : MAPPERS) {
                _verifyTruncated(_reader(mapper, StringWrapper.class, STRING_SCHEMA_JSON), doc);
            }
        }
    }

    @Test
    public void testTruncatedMapKey() throws Exception
    {
        for (int len : new int[] { HUGE_LENGTH, LONG_LENGTH, 10 }) {
            // one-entry block, then the key
            byte[] doc = _concat(_lengthPrefix(1), _concat(_lengthPrefix(len), new byte[3]));
            for (AvroMapper mapper : MAPPERS) {
                _verifyTruncated(_reader(mapper, MapWrapper.class, MAP_SCHEMA_JSON), doc);
            }
        }
    }

    @Test
    public void testNegativeStringLength() throws Exception
    {
        for (AvroMapper mapper : MAPPERS) {
            for (JacksonException e : _readFailures(_reader(mapper, StringWrapper.class, STRING_SCHEMA_JSON),
                    _lengthPrefix(-1))) {
                verifyException(e, "Invalid length indicator for String: -1");
            }
        }
    }

    @Test
    public void testValidStrings() throws Exception
    {
        for (AvroMapper mapper : MAPPERS) {
            _verifyValid(mapper, "");
            _verifyValid(mapper, "a");
            _verifyValid(mapper, _longString());
        }
    }

    // For Apache backend, limit is checked both up front (declared length long
    // enough to exceed limit whatever the content) and after decoding
    @Test
    public void testStringLengthLimit() throws Exception
    {
        for (AvroMapper mapper : LIMITED_MAPPERS) {
            // At the limit: fine, including multi-byte content longer (in bytes) than limit
            _verifyValid(mapper, _repeat("a", MAX_STRING_LENGTH));
            _verifyValid(mapper, _repeat("€", MAX_STRING_LENGTH));

            // Over the limit: one char over (only detected once decoded), and
            // long enough to be rejected based on declared length
            for (int len : new int[] { MAX_STRING_LENGTH + 1, 5 * MAX_STRING_LENGTH }) {
                _verifyTooLong(mapper, _repeat("a", len));
            }
        }

        // Apache backend's up-front check reports actual (byte) length
        final AvroMapper apacheMapper = LIMITED_MAPPERS[1];
        final String tooLong = _repeat("a", 5 * MAX_STRING_LENGTH);
        for (JacksonException e : _readFailures(_reader(apacheMapper, StringWrapper.class, STRING_SCHEMA_JSON),
                _stringDoc(apacheMapper, tooLong))) {
            verifyException(e, "String value length (5000 bytes, so at least 1666 chars)");
        }
    }

    /*
    /**********************************************************************
    /* Helper methods, verification
    /**********************************************************************
     */

    // Verifies that `value` is read back as-is both as `string` value and as Map
    // key (followed by another entry), from both `byte[]` and stream
    private void _verifyValid(AvroMapper mapper, String value) throws Exception
    {
        ObjectReader r = _reader(mapper, StringWrapper.class, STRING_SCHEMA_JSON);
        byte[] doc = _stringDoc(mapper, value);
        assertEquals(value, r.<StringWrapper>readValue(doc).s);
        assertEquals(value, r.<StringWrapper>readValue(_stream(doc)).s);

        Map<String, Integer> map = _map(value);
        r = _reader(mapper, MapWrapper.class, MAP_SCHEMA_JSON);
        doc = _mapDoc(mapper, map);
        assertEquals(map, r.<MapWrapper>readValue(doc).m);
        assertEquals(map, r.<MapWrapper>readValue(_stream(doc)).m);
    }

    private void _verifyTooLong(AvroMapper mapper, String value) throws Exception
    {
        // (exception may be wrapped by databind, so only verify message)
        for (JacksonException e : _readFailures(_reader(mapper, StringWrapper.class, STRING_SCHEMA_JSON),
                _stringDoc(mapper, value))) {
            verifyException(e, "exceeds the maximum allowed ("+MAX_STRING_LENGTH);
        }
        for (JacksonException e : _readFailures(_reader(mapper, MapWrapper.class, MAP_SCHEMA_JSON),
                _mapDoc(mapper, _map(value)))) {
            verifyException(e, "exceeds the maximum allowed ("+MAX_STRING_LENGTH);
        }
    }

    private void _verifyTruncated(ObjectReader r, byte[] doc)
    {
        for (JacksonException e : _readFailures(r, doc)) {
            // Apache decoder reports end-of-input as `EOFException`, wrapped
            if (!(e.getCause() instanceof EOFException)) {
                verifyException(e, "end-of-input");
            }
        }
    }

    // Reads `doc` both from `byte[]` and stream, expecting both to fail
    private static List<JacksonException> _readFailures(ObjectReader r, byte[] doc) {
        return Arrays.asList(
                assertThrows(JacksonException.class, () -> r.readValue(doc)),
                assertThrows(JacksonException.class, () -> r.readValue(_stream(doc))));
    }

    /*
    /**********************************************************************
    /* Helper methods, set up
    /**********************************************************************
     */

    private static AvroMapper[] _mappers(int maxStringLength) {
        final StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxStringLength(maxStringLength).build();
        return new AvroMapper[] {
                new AvroMapper(AvroFactory.builderWithNativeDecoder()
                        .streamReadConstraints(constraints).build()),
                new AvroMapper(AvroFactory.builderWithApacheDecoder()
                        .streamReadConstraints(constraints).build()) };
    }

    private static ObjectReader _reader(AvroMapper mapper, Class<?> type, String schemaJson)
            throws IOException {
        return mapper.readerFor(type).with(mapper.schemaFrom(schemaJson));
    }

    private static byte[] _stringDoc(AvroMapper mapper, String value) throws IOException {
        return mapper.writer(mapper.schemaFrom(STRING_SCHEMA_JSON))
                .writeValueAsBytes(new StringWrapper(value));
    }

    private static byte[] _mapDoc(AvroMapper mapper, Map<String, Integer> map) throws IOException {
        return mapper.writer(mapper.schemaFrom(MAP_SCHEMA_JSON))
                .writeValueAsBytes(new MapWrapper(map));
    }

    // Map with given key, followed by another entry
    private static Map<String, Integer> _map(String key) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(key, 1);
        map.put("next", 2);
        return map;
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
