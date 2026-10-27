package tools.jackson.dataformat.cbor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#882]: nextNameMatch() END_OBJECT must pop the stringref
// nesting like nextToken() does (as do nextName() and nextName(SerializableString))
public class StringRef882Test extends CBORTestBase
{
    enum NameMode { NEXT_TOKEN, NEXT_NAME, NEXT_NAME_SSTR, NEXT_NAME_MATCH }

    final static List<String> NAMES = Arrays.asList("kkk", "q");

    final static SerializableString SSTR_KKK = new SerializedString("kkk");

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

    // 256([ 256([ {"kkk": {"q": 1}} ]), "aaaa", 25(0) ])
    // Objects have no namespace of their own: a missed pop on closing them
    // throws off nesting depth so that the inner namespace (holding "kkk")
    // is not popped on its END_ARRAY; 25(0) would then be "kkk", not "aaaa"
    final static byte[] DOC_UNTAGGED_DEFINITE = {
        (byte) 0xD9, 0x01, 0x00, (byte) 0x83,
          (byte) 0xD9, 0x01, 0x00, (byte) 0x81,
            (byte) 0xA1,
              0x63, 'k', 'k', 'k',
              (byte) 0xA1, 0x61, 'q', 0x01,
          0x64, 'a', 'a', 'a', 'a',
          (byte) 0xD8, 0x19, 0x00
    };

    // Same, but with indefinite-length Objects
    final static byte[] DOC_UNTAGGED_INDEFINITE = {
        (byte) 0xD9, 0x01, 0x00, (byte) 0x83,
          (byte) 0xD9, 0x01, 0x00, (byte) 0x81,
            (byte) 0xBF,
              0x63, 'k', 'k', 'k',
              (byte) 0xBF, 0x61, 'q', 0x01, (byte) 0xFF,
            (byte) 0xFF,
          0x64, 'a', 'a', 'a', 'a',
          (byte) 0xD8, 0x19, 0x00
    };

    @ParameterizedTest
    @EnumSource(NameMode.class)
    public void testDefinite(NameMode mode) throws Exception {
        _verify(DOC_DEFINITE, mode, false);
    }

    @ParameterizedTest
    @EnumSource(NameMode.class)
    public void testIndefinite(NameMode mode) throws Exception {
        _verify(DOC_INDEFINITE, mode, false);
    }

    @ParameterizedTest
    @EnumSource(NameMode.class)
    public void testUntaggedObjectDefinite(NameMode mode) throws Exception {
        _verify(DOC_UNTAGGED_DEFINITE, mode, true);
    }

    @ParameterizedTest
    @EnumSource(NameMode.class)
    public void testUntaggedObjectIndefinite(NameMode mode) throws Exception {
        _verify(DOC_UNTAGGED_INDEFINITE, mode, true);
    }

    private void _verify(byte[] doc, NameMode mode, boolean wrapped) throws Exception {
        final PropertyNameMatcher matcher = SimpleNameMatcher.construct(Locale.ROOT, NAMES);
        try (JsonParser p = cborParser(doc)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            if (wrapped) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
            }
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("kkk", _name(p, matcher, mode));
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("q", _name(p, matcher, mode));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertNull(_name(p, matcher, mode));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(_name(p, matcher, mode));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            if (wrapped) {
                assertToken(JsonToken.END_ARRAY, p.nextToken());
            }
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("aaaa", p.getString());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("aaaa", p.getString());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
        }
    }

    private String _name(JsonParser p, PropertyNameMatcher matcher, NameMode mode) {
        switch (mode) {
        case NEXT_TOKEN:
            return (p.nextToken() == JsonToken.PROPERTY_NAME) ? p.currentName() : null;
        case NEXT_NAME:
            return p.nextName();
        case NEXT_NAME_SSTR:
            if (p.nextName(SSTR_KKK)) {
                return "kkk";
            }
            return (p.currentToken() == JsonToken.PROPERTY_NAME) ? p.currentName() : null;
        case NEXT_NAME_MATCH:
        default:
            int ix = p.nextNameMatch(matcher);
            if (ix >= 0) {
                return NAMES.get(ix);
            }
            assertEquals(PropertyNameMatcher.MATCH_END_OBJECT, ix);
            return null;
        }
    }
}
