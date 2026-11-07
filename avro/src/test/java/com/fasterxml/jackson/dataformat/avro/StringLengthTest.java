package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        for (AvroMapper mapper : new AvroMapper[] { JACKSON_MAPPER, APACHE_MAPPER }) {
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
