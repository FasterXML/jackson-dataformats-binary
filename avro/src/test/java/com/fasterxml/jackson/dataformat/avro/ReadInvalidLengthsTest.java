package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

import static org.junit.jupiter.api.Assertions.*;

// Invalid lengths and item counts must be reported when reading (not skipping)
// values; skipping is covered by `schemaev.SkipNegativeBlockSizeTest`
public class ReadInvalidLengthsTest extends AvroTestBase
{
    static class BytesValue {
        public byte[] data;
    }

    static class ListValue {
        public List<Integer> list;
    }

    static class MapValue {
        public Map<String, Integer> map;
    }

    private final AvroMapper NATIVE_MAPPER = new AvroMapper(
            AvroFactory.builderWithNativeDecoder().build());

    // NOTE: `AvroFactory.builderWithApacheDecoder()` does not (yet) produce
    // Apache-backed parsers, so need to use `ApacheAvroFactory` directly
    private final AvroMapper APACHE_MAPPER = AvroMapper.builder(new ApacheAvroFactory()).build();

    // zigzag + varint encoding of `Long.MIN_VALUE`, followed by byte size of 0
    // and end-of-array/map marker
    private final static byte[] MIN_LONG_COUNT_DOC = new byte[] {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01,
            0x00, 0x00
    };

    @Test
    public void testNegativeBytesLength() throws Exception
    {
        final String schemaJson = aposToQuotes("{'type':'record','name':'Root','fields':["
                + "{'name':'data','type':'bytes'}]}");
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            // length of -1 (zigzag 0x01)
            StreamReadException e = assertThrows(StreamReadException.class,
                    () -> mapper.readerFor(BytesValue.class)
                        .with(mapper.schemaFrom(schemaJson))
                        .readValue(new byte[] { 0x01 }));
            verifyException(e, "Invalid length indicator for Bytes: -1");
        }
    }

    // Declared length of Bytes value must be checked against max document length
    // before reading content
    @Test
    public void testBytesLengthTooLong() throws Exception
    {
        final int maxDocLen = 1000;
        final String schemaJson = aposToQuotes("{'type':'record','name':'Root','fields':["
                + "{'name':'data','type':'bytes'}]}");
        // length 1,000,000 (zigzag 2,000,000 as varint); content missing
        final byte[] doc = new byte[] { (byte) 0x80, (byte) 0x89, (byte) 0x7A };
        for (AvroFactory f : new AvroFactory[] { new AvroFactory(), new ApacheAvroFactory() }) {
            f.setStreamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(maxDocLen).build());
            final AvroMapper mapper = AvroMapper.builder(f).build();
            StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                    () -> mapper.readerFor(BytesValue.class)
                        .with(mapper.schemaFrom(schemaJson))
                        .readValue(new ByteArrayInputStream(doc)));
            verifyException(e, "exceeds the maximum allowed ("+maxDocLen);
        }
    }

    // `Long.MIN_VALUE` cannot be negated into a valid item count. NOTE: native
    // decoder only; Apache decoder rejects it with its own (non-Jackson) exception
    @Test
    public void testMinLongArrayItemCount() throws Exception
    {
        final String schemaJson = aposToQuotes("{'type':'record','name':'Root','fields':["
                + "{'name':'list','type':{'type':'array','items':'int'}}]}");
        StreamReadException e = assertThrows(StreamReadException.class,
                () -> NATIVE_MAPPER.readerFor(ListValue.class)
                    .with(NATIVE_MAPPER.schemaFrom(schemaJson))
                    .readValue(MIN_LONG_COUNT_DOC));
        verifyException(e, "Invalid item count for Array/Map block: "+Long.MIN_VALUE);
    }

    @Test
    public void testMinLongMapItemCount() throws Exception
    {
        final String schemaJson = aposToQuotes("{'type':'record','name':'Root','fields':["
                + "{'name':'map','type':{'type':'map','values':'int'}}]}");
        StreamReadException e = assertThrows(StreamReadException.class,
                () -> NATIVE_MAPPER.readerFor(MapValue.class)
                    .with(NATIVE_MAPPER.schemaFrom(schemaJson))
                    .readValue(MIN_LONG_COUNT_DOC));
        verifyException(e, "Invalid item count for Array/Map block: "+Long.MIN_VALUE);
    }
}
