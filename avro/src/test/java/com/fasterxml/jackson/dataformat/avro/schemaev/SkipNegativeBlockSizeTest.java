package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.*;

// Skipping an Array- or Map-valued field (one not in reader schema) must reject
// a block with negative byte size, instead of moving input pointer backwards
public class SkipNegativeBlockSizeTest extends AvroTestBase
{
    static class Reader {
        public int after;
    }

    private final static String ARRAY_WRITER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'list','type':{'type':'array','items':'int'}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String MAP_WRITER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'map','type':{'type':'map','values':'int'}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String READER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'after','type':'int'}"
            + "]}");

    // block count of -1 (zigzag 0x01), byte size of -2 (zigzag 0x03): skipping
    // -2 bytes would rewind back to the block count, and loop forever
    private final static byte[] DOC = new byte[] { 0x01, 0x03, 0x00, 0x0E };

    private final AvroMapper MAPPER = new AvroMapper(AvroFactory.builderWithNativeDecoder().build());

    @Test
    public void testSkipArrayWithNegativeBlockSize() throws Exception {
        _verifyFail(ARRAY_WRITER_SCHEMA_JSON);
    }

    @Test
    public void testSkipMapWithNegativeBlockSize() throws Exception {
        _verifyFail(MAP_WRITER_SCHEMA_JSON);
    }

    private void _verifyFail(String writerSchemaJson) throws Exception
    {
        final AvroSchema writerSchema = MAPPER.schemaFrom(writerSchemaJson);
        final AvroSchema readerSchema = MAPPER.schemaFrom(READER_SCHEMA_JSON);
        StreamReadException e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(StreamReadException.class,
                        () -> MAPPER.readerFor(Reader.class)
                            .with(writerSchema.withReaderSchema(readerSchema))
                            .readValue(DOC)));
        verifyException(e, "Invalid byte size for Array/Map block: -2");
    }
}
