package tools.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.testutil.LimitingInputStream;

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
            +" optional N n = 4;\n"
            +"}\n"
            +"message N {\n"
            +" optional int32 x = 1;\n"
            +" optional bytes y = 2;\n"
            +" optional int32 z = 3;\n"
            +"}\n"
    ;

    final static List<String> NAMES = Arrays.asList("c", "b", "a", "n", "x", "y", "z");

    // c=7 (tag at 0), b=[1,2,3] (tag at 2), a=5 (tag at 7)
    final static byte[] DOC = new byte[] { 8, 7, 18, 3, 1, 2, 3, 24, 5 };

    // c=7 (tag at 0), unknown #9=1 (tag at 2), a=5 (tag at 4)
    final static byte[] DOC_WITH_UNKNOWN = new byte[] { 8, 7, 72, 1, 24, 5 };

    // n (tag at 0) = { x=7 (tag at 2), y=[1,2] (tag at 4), unknown #9=1 (tag at 8),
    //   z=5 (tag at 10) }
    final static byte[] DOC_NESTED = new byte[] { 34, 10, 8, 7, 18, 2, 1, 2, 72, 1, 24, 5 };

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
            if (p.currentToken() == null) { // closed
                assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, ix);
            } else {
                assertEquals(PropertyNameMatcher.MATCH_END_OBJECT, ix);
            }
            return null;
        });
    }

    private void _verify(NameReader r) throws Exception
    {
        _verifyKnown(r);
        _verifyUnknownSkipped(r);
        _verifyNested(r);
        _verifyCloseWithUnreadValue(r);
    }

    private void _verifyKnown(NameReader r) throws Exception
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
            // not observable via API, so check directly
            assertNull(((ProtobufParser) p)._binaryValue);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(5, p.getIntValue());

            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(p.nextToken());
        }
    }

    // Location of a property name must point to its own tag, not to that
    // of the unknown field skipped before it
    private void _verifyUnknownSkipped(NameReader r) throws Exception
    {
        try (JsonParser p = MAPPER.reader(SCHEMA)
                .with(StreamReadFeature.IGNORE_UNDEFINED)
                .createParser(DOC_WITH_UNKNOWN)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("c", r.nextName(p));
            assertEquals(0, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());

            assertEquals("a", r.nextName(p));
            assertEquals(4, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(5, p.getIntValue());

            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(p.nextToken());
        }
    }

    // Same checks for fields of a nested message, which use a separate code path
    private void _verifyNested(NameReader r) throws Exception
    {
        try (JsonParser p = MAPPER.reader(SCHEMA)
                .with(StreamReadFeature.IGNORE_UNDEFINED)
                .createParser(DOC_NESTED)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("n", r.nextName(p));
            assertEquals(0, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertEquals("x", r.nextName(p));
            assertEquals(2, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());

            assertEquals("y", r.nextName(p));
            assertEquals(4, p.currentTokenLocation().getByteOffset());
            assertThrows(StreamReadException.class, () -> p.getIntValue());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertArrayEquals(new byte[] { 1, 2 }, p.getBinaryValue());

            assertEquals("z", r.nextName(p));
            assertEquals(10, p.currentTokenLocation().getByteOffset());
            assertNull(((ProtobufParser) p)._binaryValue);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(5, p.getIntValue());

            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(p.nextToken());
        }
    }

    // Closing parser with an unread (lazily skipped) value must not leave
    // a pending skip to be done against closed input
    private void _verifyCloseWithUnreadValue(NameReader r) throws Exception
    {
        final byte[] bytes = new byte[100];
        final byte[] doc = new byte[2 + bytes.length];
        doc[0] = 18; // b
        doc[1] = (byte) bytes.length;
        // small reads so that the value is not fully buffered
        JsonParser p = MAPPER.reader(SCHEMA).createParser(
                new LimitingInputStream(new ByteArrayInputStream(doc), 1));
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertEquals("b", r.nextName(p));
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        p.close();
        assertNull(r.nextName(p));
        assertNull(p.nextToken());
    }

    private ProtobufSchema _schema() {
        try {
            return MAPPER.schemaLoader().parse(PROTOC);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
