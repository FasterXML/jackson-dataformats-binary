package tools.jackson.dataformat.cbor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#882]: nextNameMatch() END_OBJECT must pop the stringref
// nesting like nextToken() does
public class StringRef882Test extends CBORTestBase
{
    final static List<String> NAMES = Arrays.asList("kkk", "q");

    // 256([ 256({"kkk": {"q": 1}}), "aaaa", 25(0) ])
    // inner namespace holds "kkk"; outer one holds "aaaa", so 25(0) is "aaaa"
    final static byte[] DOC_DEFINITE = {
        (byte) 0xD9, 0x01, 0x00, (byte) 0x83,
          (byte) 0xD9, 0x01, 0x00, (byte) 0xA1,
            0x63, 'k', 'k', 'k',
            (byte) 0xA1, 0x61, 'q', 0x01,
          0x64, 'a', 'a', 'a', 'a',
          (byte) 0xD8, 0x19, 0x00
    };

    // Same, but with indefinite-length Objects
    final static byte[] DOC_INDEFINITE = {
        (byte) 0xD9, 0x01, 0x00, (byte) 0x83,
          (byte) 0xD9, 0x01, 0x00, (byte) 0xBF,
            0x63, 'k', 'k', 'k',
            (byte) 0xBF, 0x61, 'q', 0x01, (byte) 0xFF,
          (byte) 0xFF,
          0x64, 'a', 'a', 'a', 'a',
          (byte) 0xD8, 0x19, 0x00
    };

    @Test
    public void testDefiniteNextToken() throws Exception {
        _verify(DOC_DEFINITE, false);
    }

    @Test
    public void testDefiniteNextNameMatch() throws Exception {
        _verify(DOC_DEFINITE, true);
    }

    @Test
    public void testIndefiniteNextToken() throws Exception {
        _verify(DOC_INDEFINITE, false);
    }

    @Test
    public void testIndefiniteNextNameMatch() throws Exception {
        _verify(DOC_INDEFINITE, true);
    }

    private void _verify(byte[] doc, boolean useMatcher) throws Exception {
        final PropertyNameMatcher matcher = SimpleNameMatcher.construct(Locale.ROOT, NAMES);
        try (JsonParser p = cborParser(doc)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("kkk", _name(p, matcher, useMatcher));
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("q", _name(p, matcher, useMatcher));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertNull(_name(p, matcher, useMatcher));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(_name(p, matcher, useMatcher));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("aaaa", p.getString());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("aaaa", p.getString());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
        }
    }

    private String _name(JsonParser p, PropertyNameMatcher matcher, boolean useMatcher) {
        if (!useMatcher) {
            return (p.nextToken() == JsonToken.PROPERTY_NAME) ? p.currentName() : null;
        }
        int ix = p.nextNameMatch(matcher);
        if (ix >= 0) {
            return NAMES.get(ix);
        }
        assertEquals(PropertyNameMatcher.MATCH_END_OBJECT, ix);
        return null;
    }
}
