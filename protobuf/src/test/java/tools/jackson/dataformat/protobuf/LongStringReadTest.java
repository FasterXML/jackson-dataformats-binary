package tools.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#824]: `maxStringLength` must be enforced for long String
// values (ones not fully contained in input buffer) regardless of accessor
// used, even if value fits in a single (recycled) text buffer segment;
// and `maxNameLength` for map keys
public class LongStringReadTest extends ProtobufTestBase
{
    final protected static String PROTOC_VALUE =
            "message Value {\n"
            +" optional string value = 1;\n"
            +"}\n"
    ;

    static class Value {
        public String value;

        public Value() { }
        public Value(String v) { value = v; }
    }

    private final static int MAX_STRING_LEN = 10;

    private final ProtobufMapper MAPPER_VANILLA = newObjectMapper();

    private final static int MAX_NAME_LEN = 100;

    private final ProtobufSchema SCHEMA = _schema(PROTOC_VALUE, "Value");

    private final ProtobufSchema MAP_SCHEMA = _schema(
            "syntax = \"proto3\";\n"
            + "message Msg {\n"
            + "  map<string, int32> counts = 1;\n"
            + "}\n", "Msg");

    @Test
    public void testLongValueViaStringCharacters() throws Exception
    {
        _verifyFails(p -> "char["+p.getStringCharacters().length+"]");
    }

    @Test
    public void testLongValueViaStringLength() throws Exception
    {
        _verifyFails(p -> "length "+p.getStringLength());
    }

    @Test
    public void testLongValueViaStringWriter() throws Exception
    {
        _verifyFails(p -> "chars written: "+p.getString(new StringWriter()));
    }

    // Values within limit must still be accepted
    @Test
    public void testValueWithinLimit() throws Exception
    {
        final ProtobufMapper mapper = _constrainedMapper();
        final String value = "abcdeñ";
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(new ByteArrayInputStream(_doc(value)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(value, new String(p.getStringCharacters(),
                    p.getStringOffset(), p.getStringLength()));
            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }
    }

    // 4-byte UTF-8 character right after ASCII content that fills the first
    // text buffer segment (of 200 chars) used to throw ArrayIndexOutOfBoundsException
    @Test
    public void testLongValueWithSurrogatePairAtSegmentBoundary() throws Exception
    {
        final ProtobufMapper mapper = newObjectMapper();
        final String value = "a".repeat(200) + "\uD83D\uDE00" + "b".repeat(9000);
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(new ByteArrayInputStream(_doc(value)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(value, p.getString());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }
    }

    // Map keys are exposed as property names, so `maxNameLength` (and not
    // `maxStringLength`) applies: both for keys longer than input buffer...
    @Test
    public void testLongMapKeyExceedsMaxNameLength() throws Exception
    {
        _verifyMapKeyFails("k".repeat(9000));
    }

    // ... and shorter ones
    @Test
    public void testShortMapKeyExceedsMaxNameLength() throws Exception
    {
        _verifyMapKeyFails("k".repeat(200));
    }

    // and with default limits, long keys are fine
    @Test
    public void testLongMapKeyWithinLimits() throws Exception
    {
        final ProtobufMapper mapper = newObjectMapper();
        final String key = "k".repeat(9000);
        try (JsonParser p = mapper.reader().with(MAP_SCHEMA)
                .createParser(new ByteArrayInputStream(_mapDoc(key)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals(key, p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }
    }

    private void _verifyMapKeyFails(String key) throws Exception
    {
        final ProtobufMapper mapper = new ProtobufMapper(ProtobufFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNameLength(MAX_NAME_LEN)
                        .build())
                .build());
        try (JsonParser p = mapper.reader().with(MAP_SCHEMA)
                .createParser(new ByteArrayInputStream(_mapDoc(key)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            JsonToken t = p.nextToken();
            fail("Should not pass, got "+t+" (name length "+p.currentName().length()+")");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Name length");
        }
    }

    private byte[] _mapDoc(String key) throws Exception {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put(key, 1);
        return MAPPER_VANILLA.writer(MAP_SCHEMA)
                .writeValueAsBytes(Collections.singletonMap("counts", counts));
    }

    private void _verifyFails(Function<JsonParser, String> accessor) throws Exception
    {
        final ProtobufMapper mapper = _constrainedMapper();
        // First: grow text buffer (to be recycled) by reading a value fully
        // contained in input; rejected, but only after buffer was grown
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(_doc("b".repeat(20_000)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getString();
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        }

        // Then read value longer than input buffer, from stream: decoded
        // using (recycled) segment big enough to contain all of it
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(new ByteArrayInputStream(_doc("a".repeat(10_000))))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            String result = accessor.apply(p);
            fail("Should not pass, got "+result);
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        }
    }

    // New mapper (and factory) for each test, to have separate buffer recycler pool
    private ProtobufMapper _constrainedMapper() {
        return new ProtobufMapper(ProtobufFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LEN)
                        .build())
                .build());
    }

    private byte[] _doc(String value) throws Exception {
        return MAPPER_VANILLA.writerFor(Value.class).with(SCHEMA)
                .writeValueAsBytes(new Value(value));
    }

    private static ProtobufSchema _schema(String proto, String rootType) {
        try {
            return ProtobufSchemaLoader.std.parse(proto, rootType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
