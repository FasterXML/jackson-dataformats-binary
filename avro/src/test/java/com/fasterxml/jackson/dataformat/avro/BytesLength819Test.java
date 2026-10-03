package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;
import com.fasterxml.jackson.dataformat.avro.testsupport.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#819]: `bytes` value with declared length exceeding
// available content should fail on end-of-input, without first allocating
// a buffer for the full declared length
public class BytesLength819Test extends AvroTestBase
{
    static class BytesWrapper {
        public byte[] b;

        protected BytesWrapper() { }
        public BytesWrapper(byte[] b) { this.b = b; }
    }

    private final static String SCHEMA_JSON = aposToQuotes("{'type':'record','name':'BytesWrapper',"
            +"'fields':[{'name':'b','type':'bytes'}]}");

    private final AvroMapper JACKSON_MAPPER = AvroMapper.builder(new AvroFactory()).build();
    private final AvroMapper APACHE_MAPPER = AvroMapper.builder(new ApacheAvroFactory()).build();

    // Longer than `AvroParserImpl.LONGEST_NON_CHUNKED_BINARY_READ`, not a multiple of it
    private final static int LONG_LENGTH = 777_777;

    @Test
    public void testTruncatedHugeLength() throws Exception
    {
        // length prefix for `Integer.MAX_VALUE`, followed by no content
        _testTruncated(_lengthPrefix(Integer.MAX_VALUE));
    }

    @Test
    public void testTruncatedLongLength() throws Exception
    {
        _testTruncated(_concat(_lengthPrefix(LONG_LENGTH), new byte[1000]));
    }

    @Test
    public void testTruncatedShortLength() throws Exception
    {
        _testTruncated(_concat(_lengthPrefix(10), new byte[3]));
    }

    @Test
    public void testLongValidValue() throws Exception
    {
        byte[] data = new byte[LONG_LENGTH];
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) (i * 7);
        }
        _testValid(JACKSON_MAPPER, data);
        _testValid(APACHE_MAPPER, data);
    }

    // Same for `fixed`, where size comes from the schema (no length prefix in content)
    @Test
    public void testTruncatedFixed() throws Exception
    {
        _testTruncatedFixed(Integer.MAX_VALUE - 8, new byte[1000]);
        _testTruncatedFixed(LONG_LENGTH, new byte[1000]);
        _testTruncatedFixed(10, new byte[3]);
    }

    @Test
    public void testLongValidFixed() throws Exception
    {
        byte[] data = new byte[LONG_LENGTH];
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) (i * 7);
        }
        final String schemaJson = _fixedSchema(LONG_LENGTH);
        for (AvroMapper mapper : new AvroMapper[] { JACKSON_MAPPER, APACHE_MAPPER }) {
            final AvroSchema schema = mapper.schemaFrom(schemaJson);
            byte[] doc = mapper.writer(schema).writeValueAsBytes(new BytesWrapper(data));
            final ObjectReader r = mapper.readerFor(BytesWrapper.class).with(schema);
            assertTrue(Arrays.equals(data, ((BytesWrapper) r.readValue(doc)).b));
            assertTrue(Arrays.equals(data, ((BytesWrapper) r.readValue(
                    ThrottledInputStream.wrap(new ByteArrayInputStream(doc), 1000))).b));
        }
    }

    // Apache parser allocates its scratch buffer lazily for `byte[]` input: must be released on close
    @Test
    public void testApacheBufferReleasedOnClose() throws Exception
    {
        final AvroSchema schema = APACHE_MAPPER.schemaFrom(SCHEMA_JSON);
        byte[] doc = APACHE_MAPPER.writer(schema).writeValueAsBytes(new BytesWrapper(new byte[LONG_LENGTH]));
        for (boolean useStream : new boolean[] { false, true }) {
            AvroParser p = useStream
                    ? (AvroParser) APACHE_MAPPER.getFactory().createParser(new ByteArrayInputStream(doc))
                    : (AvroParser) APACHE_MAPPER.getFactory().createParser(doc);
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertEquals(LONG_LENGTH, p.getBinaryValue().length);
            assertNotNull(_inputBuffer(p)); // chunked read must have used one
            p.close();
            assertNull(_inputBuffer(p));
        }
    }

    private static Object _inputBuffer(AvroParser p) throws Exception {
        java.lang.reflect.Field f = p.getClass().getDeclaredField("_inputBuffer");
        f.setAccessible(true);
        return f.get(p);
    }

    private static String _fixedSchema(int size) {
        return aposToQuotes("{'type':'record','name':'BytesWrapper','fields':[{'name':'b',"
                +"'type':{'type':'fixed','name':'Fix','size':"+size+"}}]}");
    }

    private void _testTruncatedFixed(int size, byte[] doc) throws Exception
    {
        final String schemaJson = _fixedSchema(size);
        final ObjectReader jacksonR = JACKSON_MAPPER.readerFor(BytesWrapper.class)
                .with(JACKSON_MAPPER.schemaFrom(schemaJson));
        try {
            jacksonR.readValue(doc);
            fail("Should not pass (byte[] input)");
        } catch (StreamReadException e) {
            verifyException(e, "end-of-input");
        }
        try {
            jacksonR.readValue(_stream(doc));
            fail("Should not pass (InputStream input)");
        } catch (StreamReadException e) {
            verifyException(e, "end-of-input");
        }
        final ObjectReader apacheR = APACHE_MAPPER.readerFor(BytesWrapper.class)
                .with(APACHE_MAPPER.schemaFrom(schemaJson));
        try {
            apacheR.readValue(doc);
            fail("Should not pass (byte[] input)");
        } catch (EOFException e) { }
        try {
            apacheR.readValue(_stream(doc));
            fail("Should not pass (InputStream input)");
        } catch (EOFException e) { }
    }

    private void _testTruncated(byte[] doc) throws Exception
    {
        final ObjectReader jacksonR = _reader(JACKSON_MAPPER);
        try {
            jacksonR.readValue(doc);
            fail("Should not pass (byte[] input)");
        } catch (StreamReadException e) {
            verifyException(e, "reached end-of-input");
        }
        try {
            jacksonR.readValue(_stream(doc));
            fail("Should not pass (InputStream input)");
        } catch (StreamReadException e) {
            verifyException(e, "reached end-of-input");
        }

        // Apache decoder reports end-of-input as-is
        final ObjectReader apacheR = _reader(APACHE_MAPPER);
        try {
            apacheR.readValue(doc);
            fail("Should not pass (byte[] input)");
        } catch (EOFException e) {
            ;
        }
        try {
            apacheR.readValue(_stream(doc));
            fail("Should not pass (InputStream input)");
        } catch (EOFException e) {
            ;
        }
    }

    private static InputStream _stream(byte[] doc) {
        return ThrottledInputStream.wrap(new ByteArrayInputStream(doc), 7);
    }

    private void _testValid(AvroMapper mapper, byte[] data) throws Exception
    {
        final AvroSchema schema = mapper.schemaFrom(SCHEMA_JSON);
        byte[] doc = mapper.writer(schema).writeValueAsBytes(new BytesWrapper(data));
        final ObjectReader r = _reader(mapper);

        BytesWrapper result = r.readValue(doc);
        assertTrue(Arrays.equals(data, result.b));

        result = r.readValue(ThrottledInputStream.wrap(new ByteArrayInputStream(doc), 1000));
        assertTrue(Arrays.equals(data, result.b));
    }

    private ObjectReader _reader(AvroMapper mapper) throws Exception {
        return mapper.readerFor(BytesWrapper.class)
                .with(mapper.schemaFrom(SCHEMA_JSON));
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
