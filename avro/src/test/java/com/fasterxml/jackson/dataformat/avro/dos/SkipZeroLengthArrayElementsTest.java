package com.fasterxml.jackson.dataformat.avro.dos;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.avro.*;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

import static org.junit.jupiter.api.Assertions.*;

// `JsonParser.skipChildren()` must not iterate over elements of an Array whose
// elements take no bytes (`null`, zero-length `fixed`, Records with only such fields):
// item count is not bounded by input length, so a few bytes can declare billions of
// elements. Databind uses `skipChildren()` for properties not in the POJO.
// Verified with both native and Apache decoders.
public class SkipZeroLengthArrayElementsTest extends AvroTestBase
{
    public static class Item {
        public String name;
    }

    // 2^29 items per block: Apache decoder rejects Arrays over 2^31 - 9 items (in total)
    private final static long BLOCK_COUNT = 1L << 29;

    // Iterating over all elements would take minutes
    private final static Duration TIMEOUT = Duration.ofSeconds(5);

    private final static String NULL_ARRAY = "{'type':'array','items':'null'}";

    private final static String EMPTY_RECORD_ARRAY = "{'type':'array','items':"
            + "{'type':'record','name':'Empty','fields':["
            + "{'name':'n','type':'null'},"
            + "{'name':'f','type':{'type':'fixed','name':'F0','size':0}}"
            + "]}}";

    private final static String NULL_ARRAY_RECORD = _recordSchema(NULL_ARRAY);

    static Stream<AvroMapper> mappers() {
        return Stream.of(
                AvroMapper.builder(AvroFactory.builderWithNativeDecoder().build())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build(),
                // NOTE: `AvroFactory.builderWithApacheDecoder()` does not (yet) produce
                // Apache-backed parsers, so need to use `ApacheAvroFactory` directly
                AvroMapper.builder(new ApacheAvroFactory())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build());
    }

    /*
    /**********************************************************************
    /* Streaming
    /**********************************************************************
     */

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipChildrenRootArray(AvroMapper mapper) throws Exception {
        final AvroSchema schema = mapper.schemaFrom(aposToQuotes(NULL_ARRAY));
        final byte[] doc = _hugeArray();
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try (JsonParser p = mapper.reader(schema).createParser(doc)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                p.skipChildren();
                assertToken(JsonToken.END_ARRAY, p.currentToken());
                assertNull(p.nextToken());
            }
        });
    }

    // Skipping must consume same content as reading would
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipChildrenMatchesReading(AvroMapper mapper) throws Exception {
        final AvroSchema schema = mapper.schemaFrom(aposToQuotes(NULL_ARRAY_RECORD));
        // 2 blocks of 2 items (second one with byte size) and 1 block of 1 item
        final byte[] doc = _doc(new long[] { 2, -2, 1 }, "x");

        try (JsonParser p = mapper.reader(schema).createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("tags", p.nextFieldName());
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            for (int i = 0; i < 5; ++i) {
                assertToken(JsonToken.VALUE_NULL, p.nextToken());
            }
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertEquals("name", p.nextFieldName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("x", p.getText());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }

        try (JsonParser p = mapper.reader(schema).createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("tags", p.nextFieldName());
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            p.skipChildren();
            assertToken(JsonToken.END_ARRAY, p.currentToken());
            assertEquals("name", p.nextFieldName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("x", p.getText());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    // Skipping from the middle of zero-length elements not optimized, but must work
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipChildrenAfterElement(AvroMapper mapper) throws Exception {
        final AvroSchema schema = mapper.schemaFrom(aposToQuotes(NULL_ARRAY_RECORD));
        final byte[] doc = _doc(new long[] { 3 }, "x");

        try (JsonParser p = mapper.reader(schema).createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("tags", p.nextFieldName());
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NULL, p.nextToken());
            // no-op for scalar values
            p.skipChildren();
            assertToken(JsonToken.VALUE_NULL, p.currentToken());
            assertToken(JsonToken.VALUE_NULL, p.nextToken());
            assertToken(JsonToken.VALUE_NULL, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertEquals("name", p.nextFieldName());
            assertEquals("x", p.nextTextValue());
        }
    }

    /*
    /**********************************************************************
    /* Databind: unknown properties
    /**********************************************************************
     */

    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredNullArray(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, mapper.schemaFrom(aposToQuotes(NULL_ARRAY_RECORD)));
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredEmptyRecordArray(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, mapper.schemaFrom(aposToQuotes(_recordSchema(EMPTY_RECORD_ARRAY))));
    }

    // Array within an ignored Record
    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredNestedNullArray(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, mapper.schemaFrom(aposToQuotes(_recordSchema(
                "{'type':'record','name':'Nested','fields':["
                + "{'name':'tags','type':" + NULL_ARRAY + "}]}"))));
    }

    // Array within a union
    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredUnionNullArray(AvroMapper mapper) throws Exception {
        final AvroSchema schema = mapper.schemaFrom(aposToQuotes(_recordSchema(
                "['null'," + NULL_ARRAY + "]")));
        final byte[] doc = _hugeArray();
        final byte[] unionDoc = new byte[doc.length + 1];
        unionDoc[0] = 0x02; // union index 1
        System.arraycopy(doc, 0, unionDoc, 1, doc.length);
        _verifyIgnored(mapper, schema, _appendString(unionDoc, "abc"));
    }

    // Writer schema has zero-length elements, reader schema does not: encoding
    // determined by writer schema
    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredNullArrayWithReaderSchema(AvroMapper mapper) throws Exception {
        final AvroSchema writer = mapper.schemaFrom(aposToQuotes(NULL_ARRAY_RECORD));
        final AvroSchema reader = mapper.schemaFrom(aposToQuotes(_recordSchema(
                "{'type':'array','items':['null','string']}")));
        _verifyIgnored(mapper, writer.withReaderSchema(reader));
    }

    // Skipping of Arrays with non-zero-length elements, and of empty Arrays, unchanged
    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredOtherArrays(AvroMapper mapper) throws Exception {
        AvroSchema schema = mapper.schemaFrom(aposToQuotes(_recordSchema(
                "{'type':'array','items':'int'}")));
        // 3 ints (zigzag 1, 2, 3), then end-of-array
        byte[] doc = _appendString(new byte[] { 0x06, 0x02, 0x04, 0x06, 0x00 }, "abc");
        Item item = mapper.readerFor(Item.class).with(schema).readValue(doc);
        assertEquals("abc", item.name);

        schema = mapper.schemaFrom(aposToQuotes(NULL_ARRAY_RECORD));
        doc = _appendString(new byte[] { 0x00 }, "abc");
        item = mapper.readerFor(Item.class).with(schema).readValue(doc);
        assertEquals("abc", item.name);
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private void _verifyIgnored(AvroMapper mapper, AvroSchema schema) throws Exception {
        _verifyIgnored(mapper, schema, _appendString(_hugeArray(), "abc"));
    }

    private void _verifyIgnored(AvroMapper mapper, AvroSchema schema, byte[] doc)
        throws Exception
    {
        Item item = assertTimeoutPreemptively(TIMEOUT,
                () -> mapper.readerFor(Item.class).with(schema).<Item>readValue(doc));
        assertEquals("abc", item.name);
    }

    // Record with field "tags" of given type, followed by "name" (String)
    private static String _recordSchema(String tagsType) {
        return "{'type':'record','name':'Root','fields':["
                + "{'name':'tags','type':" + tagsType + "},"
                + "{'name':'name','type':'string'}"
                + "]}";
    }

    // Array of 3 * 2^29 zero-length elements, in 3 blocks: one with negative count
    // (followed by byte size)
    private static byte[] _hugeArray() {
        return _blocks(new long[] { BLOCK_COUNT, -BLOCK_COUNT, BLOCK_COUNT });
    }

    private static byte[] _doc(long[] blockCounts, String name) {
        return _appendString(_blocks(blockCounts), name);
    }

    // Blocks of zero-length elements, followed by end marker
    private static byte[] _blocks(long[] blockCounts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (long count : blockCounts) {
            _writeLong(out, count);
            if (count < 0) {
                _writeLong(out, 0L); // byte size
            }
        }
        _writeLong(out, 0L);
        return out.toByteArray();
    }

    private static byte[] _appendString(byte[] doc, String str) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(doc, 0, doc.length);
        byte[] b = str.getBytes(StandardCharsets.UTF_8);
        _writeLong(out, b.length);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    // zigzag-encoded varint
    private static void _writeLong(ByteArrayOutputStream out, long v) {
        long z = (v << 1) ^ (v >> 63);
        while ((z & ~0x7FL) != 0L) {
            out.write((int) ((z & 0x7F) | 0x80));
            z >>>= 7;
        }
        out.write((int) z);
    }
}
