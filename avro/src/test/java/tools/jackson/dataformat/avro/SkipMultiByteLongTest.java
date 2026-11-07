package tools.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


import static org.junit.jupiter.api.Assertions.*;

// Skipping a multi-byte (varint) `long` must consume all of its bytes: native
// decoder's fast path (10+ bytes buffered) only consumed the first byte.
// Affects skipped `long` fields and byte size of Array/Map blocks with negative count
public class SkipMultiByteLongTest extends AvroTestBase
{
    static class Full {
        public long big;
        public int after;
        public String pad;

        protected Full() { }
        public Full(long big, int after, String pad) {
            this.big = big;
            this.after = after;
            this.pad = pad;
        }
    }

    static class Partial {
        public int after;
        public String pad;
    }

    static class ListValue {
        public List<Integer> list;
        public int after;
    }

    private final static String FULL_SCHEMA_JSON = a2q("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'big','type':'long'},"
            + "{'name':'after','type':'int'},"
            + "{'name':'pad','type':'string'}"
            + "]}");

    private final static String PARTIAL_SCHEMA_JSON = a2q("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'after','type':'int'},"
            + "{'name':'pad','type':'string'}"
            + "]}");

    private final static String LIST_SCHEMA_JSON = a2q("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'list','type':{'type':'array','items':'int'}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    static Stream<AvroMapper> mappers() {
        return Stream.of(
                new AvroMapper(AvroFactory.builderWithNativeDecoder().build()),
                new AvroMapper(AvroFactory.builderWithApacheDecoder().build()));
    }

    // `long` field in writer schema but not in reader schema gets skipped
    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipMultiByteLongField(AvroMapper mapper) throws Exception
    {
        final AvroSchema writerSchema = mapper.schemaFrom(FULL_SCHEMA_JSON);
        final AvroSchema readerSchema = mapper.schemaFrom(PARTIAL_SCHEMA_JSON);
        // padding so that fast path (10+ bytes buffered) is used
        final String pad = "0123456789abcdef";
        for (long big : new long[] { 64L, 1000L, -1000L, Long.MAX_VALUE, Long.MIN_VALUE }) {
            byte[] doc = mapper.writer(writerSchema).writeValueAsBytes(new Full(big, 7, pad));
            Partial result = mapper.readerFor(Partial.class)
                    .with(writerSchema.withReaderSchema(readerSchema))
                    .readValue(doc);
            assertEquals(7, result.after, "for big="+big);
            assertEquals(pad, result.pad, "for big="+big);
        }
    }

    // Array block with negative item count is followed by its byte size, which is
    // skipped when reading (not skipping) the Array
    @ParameterizedTest
    @MethodSource("mappers")
    public void testReadArrayWithMultiByteBlockSize(AvroMapper mapper) throws Exception
    {
        final int count = 64; // 64 zero ints, 1 byte each: byte size 64 needs 2 bytes
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0x7F); // count -64
        bytes.write(0x80); // byte size 64 (zigzag 128)
        bytes.write(0x01);
        for (int i = 0; i < count; ++i) {
            bytes.write(0x00);
        }
        bytes.write(0x00); // end-of-array
        bytes.write(0x0E); // `after`: 7
        ListValue result = mapper.readerFor(ListValue.class)
                .with(mapper.schemaFrom(LIST_SCHEMA_JSON))
                .readValue(bytes.toByteArray());
        assertEquals(count, result.list.size());
        for (Integer value : result.list) {
            assertEquals(Integer.valueOf(0), value);
        }
        assertEquals(7, result.after);
    }
}
