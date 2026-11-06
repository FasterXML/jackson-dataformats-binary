package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.*;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

import static org.junit.jupiter.api.Assertions.*;

// Skipping an Array- or Map-valued field (one not in reader schema) must reject
// a block with negative byte size, instead of moving input pointer backwards;
// and similarly for String- and Bytes-valued fields with negative length.
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

    private void _verifyFail(AvroMapper mapper, String writerSchemaJson, byte[] doc,
            String expMsg) throws Exception
    {
        final AvroSchema writerSchema = mapper.schemaFrom(writerSchemaJson);
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        StreamReadException e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(StreamReadException.class,
                        () -> mapper.readerFor(Reader.class)
                            .with(writerSchema.withReaderSchema(readerSchema))
                            .readValue(doc)));
        verifyException(e, expMsg);
    }

    private static String _writerSchema(String skippedType) {
        return aposToQuotes("{"
                + "'type':'record','name':'Root','fields':["
                + "{'name':'skipped','type':" + skippedType + "},"
                + "{'name':'after','type':'int'}"
                + "]}");
    }
}
