package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.avro.*;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

import static org.junit.jupiter.api.Assertions.*;

// Skipping an Array- or Map-valued field (one not in reader schema) must reject
// a block with negative byte size, instead of moving input pointer backwards;
// and similarly for String- and Bytes-valued fields with negative length.
// Also verifies other validation of skipped blocks/values (item count range,
// minimum Map block size, document length constraint).
// Verified with both native and Apache decoders.
public class SkipNegativeBlockSizeTest extends AvroTestBase
{
    static class Reader {
        public int after;
    }

    private final static String ARRAY_WRITER_SCHEMA_JSON = _writerSchema(
            "{'type':'array','items':'int'}");

    private final static String MAP_WRITER_SCHEMA_JSON = _writerSchema(
            "{'type':'map','values':'int'}");

    private final static String STRING_WRITER_SCHEMA_JSON = _writerSchema("'string'");

    private final static String BYTES_WRITER_SCHEMA_JSON = _writerSchema("'bytes'");

    private final static String READER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'after','type':'int'}"
            + "]}");

    // block count of -1 (zigzag 0x01), byte size of -2 (zigzag 0x03): skipping
    // -2 bytes would rewind back to the block count, and loop forever
    private final static byte[] BLOCK_DOC = new byte[] { 0x01, 0x03, 0x00, 0x0E };

    // length of -1 (zigzag 0x01): skipping -1 bytes would rewind back to the length
    private final static byte[] LENGTH_DOC = new byte[] { 0x01, 0x0E };

    static Stream<AvroMapper> mappers() {
        return Stream.of(
                new AvroMapper(AvroFactory.builderWithNativeDecoder().build()),
                // NOTE: `AvroFactory.builderWithApacheDecoder()` does not (yet) produce
                // Apache-backed parsers, so need to use `ApacheAvroFactory` directly
                AvroMapper.builder(new ApacheAvroFactory()).build());
    }

    static Stream<AvroMapper> constrainedMappers() {
        return Stream.of(new AvroFactory(), new ApacheAvroFactory())
                .map(f -> {
                    f.setStreamReadConstraints(StreamReadConstraints.builder()
                            .maxDocumentLength(MAX_DOC_LEN).build());
                    return AvroMapper.builder(f).build();
                });
    }

    private final static int MAX_DOC_LEN = 1000;

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipArrayWithNegativeBlockSize(AvroMapper mapper) throws Exception {
        _verifyFail(mapper, ARRAY_WRITER_SCHEMA_JSON, BLOCK_DOC,
                "Invalid byte size for Array/Map block: -2");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipMapWithNegativeBlockSize(AvroMapper mapper) throws Exception {
        _verifyFail(mapper, MAP_WRITER_SCHEMA_JSON, BLOCK_DOC,
                "Invalid byte size for Array/Map block: -2");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipStringWithNegativeLength(AvroMapper mapper) throws Exception {
        _verifyFail(mapper, STRING_WRITER_SCHEMA_JSON, LENGTH_DOC,
                "Invalid length indicator for String: -1");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipBytesWithNegativeLength(AvroMapper mapper) throws Exception {
        _verifyFail(mapper, BYTES_WRITER_SCHEMA_JSON, LENGTH_DOC,
                "Invalid length indicator for Bytes: -1");
    }

    // Map entries have keys of at least 1 byte, so block of 2 entries cannot be 1 byte
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipMapWithTooSmallBlockSize(AvroMapper mapper) throws Exception {
        // count -2, byte size 1, 1 byte of content, end-of-map, then `after`
        _verifyFail(mapper, MAP_WRITER_SCHEMA_JSON,
                new byte[] { 0x03, 0x02, 0x00, 0x00, 0x0E },
                "Invalid byte size for Map block: 1 (less than entry count 2)");
    }

    // Arrays elements may be zero-length (here, `null`s), so block of 3 elements
    // with byte size of 0 is valid
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipArrayWithZeroBlockSize(AvroMapper mapper) throws Exception {
        final AvroSchema writerSchema = mapper.schemaFrom(
                _writerSchema("{'type':'array','items':'null'}"));
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        // count -3, byte size 0, end-of-array, then `after`
        Reader r = mapper.readerFor(Reader.class)
                .with(writerSchema.withReaderSchema(readerSchema))
                .readValue(new byte[] { 0x05, 0x00, 0x00, 0x0E });
        assertEquals(7, r.after);
    }

    // Block item count is `long`, not `int`
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipArrayWithLongBlockCount(AvroMapper mapper) throws Exception {
        final AvroSchema writerSchema = mapper.schemaFrom(ARRAY_WRITER_SCHEMA_JSON);
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // count needing 6-byte varint (does not need to match content when skipping)
        _writeLong(bytes, -(1L << 40));
        _writeLong(bytes, 1L); // byte size
        bytes.write(0); // content
        bytes.write(0); // end-of-array
        bytes.write(0x0E); // `after`: 7
        Reader r = mapper.readerFor(Reader.class)
                .with(writerSchema.withReaderSchema(readerSchema))
                .readValue(bytes.toByteArray());
        assertEquals(7, r.after);
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipArrayWithMinLongBlockCount(AvroMapper mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        _writeLong(bytes, Long.MIN_VALUE);
        _writeLong(bytes, 1L);
        bytes.write(0);
        bytes.write(0);
        bytes.write(0x0E);
        _verifyFail(mapper, ARRAY_WRITER_SCHEMA_JSON, bytes.toByteArray(),
                "Invalid item count for Array/Map block: "+Long.MIN_VALUE);
    }

    // Zero-length elements (`null`s etc) consume no input, so skipping must not
    // iterate over them: item count is not bounded by input length. Use multiple
    // blocks of huge counts, as each one costs only a few bytes
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipZeroLengthArrayWithHugeCounts(AvroMapper mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 100; ++i) {
            _writeLong(bytes, (i & 1) == 0 ? Long.MAX_VALUE : Integer.MAX_VALUE);
        }
        bytes.write(0); // end-of-array
        bytes.write(0x0E); // `after`: 7
        final byte[] doc = bytes.toByteArray();
        for (String elemType : new String[] {
                "'null'",
                "{'type':'fixed','name':'Empty','size':0}",
                "{'type':'record','name':'Nulls','fields':["
                    +"{'name':'a','type':'null'},{'name':'b','type':'null'}]}",
        }) {
            _verifySkip(mapper, _writerSchema("{'type':'array','items':"+elemType+"}"), doc);
        }
    }

    // ... but non-zero-length elements do get skipped one by one
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipNonZeroLengthArray(AvroMapper mapper) throws Exception {
        // record with `null` and `int` fields: 2 blocks with 2 + 1 elements
        _verifySkip(mapper, _writerSchema("{'type':'array','items':"
                + "{'type':'record','name':'Elem','fields':["
                + "{'name':'a','type':'null'},{'name':'b','type':'int'}]}}"),
                new byte[] { 0x04, 0x02, 0x04, 0x02, 0x06, 0x00, 0x0E });
    }

    // Size of skipped block must be checked against max document length before skipping
    @ParameterizedTest
    @MethodSource("constrainedMappers")
    public void testSkipArrayBlockTooLong(AvroMapper mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        _writeLong(bytes, -1L);
        _writeLong(bytes, 1_000_000L); // byte size way above limit; content missing
        _verifyLengthFail(mapper, ARRAY_WRITER_SCHEMA_JSON, bytes.toByteArray());
    }

    // Byte size so big that adding current offset overflows must not pass the check
    @ParameterizedTest
    @MethodSource("constrainedMappers")
    public void testSkipArrayBlockSizeOverflow(AvroMapper mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        _writeLong(bytes, -1L);
        _writeLong(bytes, Long.MAX_VALUE - 5L);
        _verifyLengthFail(mapper, ARRAY_WRITER_SCHEMA_JSON, bytes.toByteArray());
    }

    @ParameterizedTest
    @MethodSource("constrainedMappers")
    public void testSkipStringTooLong(AvroMapper mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        _writeLong(bytes, 1_000_000L); // length way above limit; content missing
        _verifyLengthFail(mapper, STRING_WRITER_SCHEMA_JSON, bytes.toByteArray());
    }

    private void _verifyLengthFail(AvroMapper mapper, String writerSchemaJson, byte[] doc)
        throws Exception
    {
        final AvroSchema writerSchema = mapper.schemaFrom(writerSchemaJson);
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                () -> mapper.readerFor(Reader.class)
                    .with(writerSchema.withReaderSchema(readerSchema))
                    .readValue(new ByteArrayInputStream(doc)));
        verifyException(e, "exceeds the maximum allowed ("+MAX_DOC_LEN);
    }

    // Verifies with both `byte[]` and `InputStream` input
    private void _verifyFail(AvroMapper mapper, String writerSchemaJson, byte[] doc,
            String expMsg) throws Exception
    {
        final AvroSchema writerSchema = mapper.schemaFrom(writerSchemaJson);
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        final ObjectReader r = mapper.readerFor(Reader.class)
                .with(writerSchema.withReaderSchema(readerSchema));
        StreamReadException e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(StreamReadException.class, () -> r.readValue(doc)));
        verifyException(e, expMsg);
        e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(StreamReadException.class,
                        () -> r.readValue(new ByteArrayInputStream(doc))));
        verifyException(e, expMsg);
    }

    // Verifies with both `byte[]` and `InputStream` input
    private void _verifySkip(AvroMapper mapper, String writerSchemaJson, byte[] doc)
        throws Exception
    {
        final AvroSchema writerSchema = mapper.schemaFrom(writerSchemaJson);
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        final ObjectReader r = mapper.readerFor(Reader.class)
                .with(writerSchema.withReaderSchema(readerSchema));
        Reader result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> r.<Reader>readValue(doc));
        assertEquals(7, result.after);
        result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> r.<Reader>readValue(new ByteArrayInputStream(doc)));
        assertEquals(7, result.after);
    }

    // zigzag + varint encoding of Avro `long`
    private static void _writeLong(ByteArrayOutputStream out, long value) {
        long n = (value << 1) ^ (value >> 63);
        while ((n & ~0x7FL) != 0L) {
            out.write((int) ((n & 0x7F) | 0x80));
            n >>>= 7;
        }
        out.write((int) n);
    }

    private static String _writerSchema(String skippedType) {
        return aposToQuotes("{"
                + "'type':'record','name':'Root','fields':["
                + "{'name':'skipped','type':" + skippedType + "},"
                + "{'name':'after','type':'int'}"
                + "]}");
    }
}
