package tools.jackson.dataformat.avro.constraints;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.avro.*;

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

    // Iterating over all elements would take tens of seconds
    private final static Duration TIMEOUT = Duration.ofSeconds(5);

    // 3 blocks of 2^29 items (Apache decoder rejects Arrays over 2^31 - 9 items),
    // second one with negative count followed by byte size; then end marker
    private final static byte[] HUGE_ARRAY = {
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x04, // 2^29
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x03, // -2^29
        0x00, // byte size
        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x04, // 2^29
        0x00
    };

    // String "abc"
    private final static byte[] NAME = { 0x06, 'a', 'b', 'c' };

    private final static String NULL_ARRAY = "{'type':'array','items':'null'}";

    static Stream<AvroMapper> mappers() {
        return Stream.of(AvroFactory.builderWithNativeDecoder(), AvroFactory.builderWithApacheDecoder())
                .map(b -> AvroMapper.builder(b.build())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build());
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testSkipChildren(AvroMapper mapper) throws Exception {
        final AvroSchema schema = mapper.schemaFrom(aposToQuotes(NULL_ARRAY));
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try (JsonParser p = mapper.reader().with(schema).createParser(HUGE_ARRAY)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                p.skipChildren();
                assertToken(JsonToken.END_ARRAY, p.currentToken());
                assertNull(p.nextToken());
            }
        });
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredNullArray(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, _schema(mapper, NULL_ARRAY), HUGE_ARRAY);
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredEmptyRecordArray(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, _schema(mapper, "{'type':'array','items':"
                + "{'type':'record','name':'Empty','fields':["
                + "{'name':'n','type':'null'},"
                + "{'name':'f','type':{'type':'fixed','name':'F0','size':0}}"
                + "]}}"), HUGE_ARRAY);
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredArrayInRecord(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, _schema(mapper, "{'type':'record','name':'Nested','fields':["
                + "{'name':'tags','type':" + NULL_ARRAY + "}]}"), HUGE_ARRAY);
    }

    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredArrayInUnion(AvroMapper mapper) throws Exception {
        byte[] doc = new byte[HUGE_ARRAY.length + 1];
        doc[0] = 0x02; // union index 1
        System.arraycopy(HUGE_ARRAY, 0, doc, 1, HUGE_ARRAY.length);
        _verifyIgnored(mapper, _schema(mapper, "['null'," + NULL_ARRAY + "]"), doc);
    }

    // Encoding (and so element length) determined by writer schema
    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredArrayWithReaderSchema(AvroMapper mapper) throws Exception {
        AvroSchema reader = _schema(mapper, "{'type':'array','items':['null','string']}");
        _verifyIgnored(mapper, _schema(mapper, NULL_ARRAY).withReaderSchema(reader), HUGE_ARRAY);
    }

    // Skipping of empty Arrays, and Arrays with non-zero-length elements, unchanged
    @ParameterizedTest
    @MethodSource("mappers")
    public void testIgnoredOtherArrays(AvroMapper mapper) throws Exception {
        _verifyIgnored(mapper, _schema(mapper, NULL_ARRAY), new byte[] { 0x00 });
        // 3 ints (zigzag 1, 2, 3), then end marker
        _verifyIgnored(mapper, _schema(mapper, "{'type':'array','items':'int'}"),
                new byte[] { 0x06, 0x02, 0x04, 0x06, 0x00 });
    }

    // Record with field "tags" of given type, followed by "name" (String)
    private static AvroSchema _schema(AvroMapper mapper, String tagsType) throws Exception {
        return mapper.schemaFrom(aposToQuotes("{'type':'record','name':'Root','fields':["
                + "{'name':'tags','type':" + tagsType + "},"
                + "{'name':'name','type':'string'}"
                + "]}"));
    }

    private static void _verifyIgnored(AvroMapper mapper, AvroSchema schema, byte[] tags)
        throws Exception
    {
        byte[] doc = new byte[tags.length + NAME.length];
        System.arraycopy(tags, 0, doc, 0, tags.length);
        System.arraycopy(NAME, 0, doc, tags.length, NAME.length);
        Item item = assertTimeoutPreemptively(TIMEOUT,
                () -> mapper.readerFor(Item.class).with(schema).<Item>readValue(doc));
        assertEquals("abc", item.name);
    }
}
