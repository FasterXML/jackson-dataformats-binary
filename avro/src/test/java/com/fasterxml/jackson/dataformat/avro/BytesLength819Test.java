package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Arrays;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;
import com.fasterxml.jackson.dataformat.avro.testsupport.ThrottledInputStream;

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

    // Longer than `AvroParserImpl.LONGEST_NON_CHUNKED_BINARY`, not a multiple of it
    private final static int LONG_LENGTH = 777_777;

    public void testTruncatedHugeLength() throws Exception
    {
        // length prefix for `Integer.MAX_VALUE`, followed by no content
        _testTruncated(_lengthPrefix(Integer.MAX_VALUE));
    }

    public void testTruncatedLongLength() throws Exception
    {
        _testTruncated(_concat(_lengthPrefix(LONG_LENGTH), new byte[1000]));
    }

    public void testTruncatedShortLength() throws Exception
    {
        _testTruncated(_concat(_lengthPrefix(10), new byte[3]));
    }

    public void testLongValidValue() throws Exception
    {
        byte[] data = new byte[LONG_LENGTH];
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) (i * 7);
        }
        _testValid(JACKSON_MAPPER, data);
        _testValid(APACHE_MAPPER, data);
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
