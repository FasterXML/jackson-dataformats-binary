package tools.jackson.dataformat.protobuf;

import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#879]: nextName() / nextNameMatch() must reset per-token
// state (number type, token location) just like nextToken() does
public class NextNameTokenState879Test extends ProtobufTestBase
{
    final static String PROTOC =
            "message M {\n"
            +" optional int32 c = 1;\n"
            +" optional bytes b = 2;\n"
            +" optional int32 a = 3;\n"
            +"}\n"
    ;

    final static List<String> NAMES = Arrays.asList("c", "b", "a");

    // c=7 (tag at 0), b=[1,2,3] (tag at 2), a=5 (tag at 7)
    final static byte[] DOC = new byte[] { 8, 7, 18, 3, 1, 2, 3, 24, 5 };

    interface NameReader {
        String nextName(JsonParser p);
    }

    private final ProtobufMapper MAPPER = newObjectMapper();

    private final ProtobufSchema SCHEMA = _schema();

    @Test
    public void testNextToken() throws Exception {
        _verify(p -> (p.nextToken() == JsonToken.PROPERTY_NAME) ? p.currentName() : null);
    }

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

    private void _verify(NameReader r) throws Exception
    {
        try (JsonParser p = MAPPER.reader(SCHEMA).createParser(DOC)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("c", r.nextName(p));
            assertEquals(0, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());

            assertEquals("b", r.nextName(p));
            assertEquals(2, p.currentTokenLocation().getByteOffset());
            // must not see the previous number
            assertThrows(StreamReadException.class, () -> p.getIntValue());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertArrayEquals(new byte[] { 1, 2, 3 }, p.getBinaryValue());

            assertEquals("a", r.nextName(p));
            assertEquals(7, p.currentTokenLocation().getByteOffset());
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
}
