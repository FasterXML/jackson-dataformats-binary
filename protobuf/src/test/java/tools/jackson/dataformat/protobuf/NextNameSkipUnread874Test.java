package tools.jackson.dataformat.protobuf;

import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#874]: nextName() / nextNameMatch() must skip an unread
// String / binary value before decoding the next tag
public class NextNameSkipUnread874Test extends ProtobufTestBase
{
    final static String PROTOC =
            "message Outer {\n"
            +" optional string extra = 1;\n"
            +" optional Inner inner = 2;\n"
            +" optional int32 a = 3;\n"
            +"}\n"
            +"message Inner {\n"
            +" optional string s = 1;\n"
            +" optional bytes b = 2;\n"
            +" optional int32 c = 3;\n"
            +"}\n"
    ;

    final static List<String> NAMES = Arrays.asList("extra", "inner", "a", "s", "b", "c");

    public static class Bean {
        public int a;
    }

    interface NameReader {
        String nextName(JsonParser p);
    }

    private final ProtobufMapper MAPPER = ProtobufMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ProtobufSchema SCHEMA = _schema();

    private final byte[] DOC = _doc();

    @Test
    public void testNextName() throws Exception {
        _verify(p -> p.nextName());
    }

    @Test
    public void testNextNameSerializableString() throws Exception {
        _verify(p -> {
            // a non-matching name, to get the name itself back via currentName()
            if (p.nextName(new SerializedString("xxx"))) {
                fail("Should not match");
            }
            return (p.currentToken() == JsonToken.PROPERTY_NAME) ? p.currentName() : null;
        });
    }

    @Test
    public void testNextNameMatch() throws Exception {
        final PropertyNameMatcher matcher = SimpleNameMatcher.construct(null, NAMES);
        _verify(p -> {
            int ix = p.nextNameMatch(matcher);
            if (ix >= 0) {
                return NAMES.get(ix);
            }
            assertEquals(PropertyNameMatcher.MATCH_END_OBJECT, ix);
            return null;
        });
    }

    @Test
    public void testDatabind() throws Exception {
        Bean bean = MAPPER.readerFor(Bean.class).with(SCHEMA).readValue(DOC);
        assertEquals(5, bean.a);
    }

    private void _verify(NameReader r) throws Exception
    {
        try (JsonParser p = MAPPER.reader(SCHEMA).createParser(DOC)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("extra", r.nextName(p));
            // String value at root level, not accessed
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("inner", r.nextName(p));
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("s", r.nextName(p));
            // String value in nested message, not accessed
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("b", r.nextName(p));
            // Binary value in nested message, not accessed
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertEquals("c", r.nextName(p));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());
            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertEquals("a", r.nextName(p));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(5, p.getIntValue());
            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(p.nextToken());
        }
    }

    private ProtobufSchema _schema() {
        try {
            return MAPPER.schemaLoader().parse(PROTOC);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] _doc() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("s", "zz");
        inner.put("b", new byte[] { 0x7A, 0x7A, 0x7A });
        inner.put("c", 7);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("extra", "zz");
        value.put("inner", inner);
        value.put("a", 5);
        return MAPPER.writer(SCHEMA).writeValueAsBytes(value);
    }
}
