package tools.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.util.Named;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#887]: `nextNameMatch()` with (padded) `BinaryNameMatcher`
// constructed by `SmileFactory`: known names must match, names differing only by
// NUL or 0xFF bytes at start of last quad must not
public class NameMatchPadding887Test extends BaseTestForSmile
{
    private final static int MAX_NAME_LEN = 30;

    // UTF-8: C3 A9 C3 A9 74 C3 A9; last quad starts at character boundary
    private final static String UNICODE_NAME = "éété";

    static class Bean {
        public int x, abcde;
    }

    // Reproduction from [core#1798]
    @Test
    public void testNulPrefixedNamesVsProperties() throws Exception
    {
        final ObjectMapper mapper = smileMapperBuilder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Map<String, Object> input = new LinkedHashMap<>();
        // first name is read via `nextToken()`, only later ones via `nextNameMatch()`
        input.put("first", 1);
        input.put("\u0000x", 5);
        input.put("abcd\u0000e", 7);
        byte[] doc = mapper.writeValueAsBytes(input);
        for (int i = 0; i < 3; ++i) {
            Bean bean = mapper.readValue(doc, Bean.class);
            assertEquals(0, bean.x, "read #" + i);
            assertEquals(0, bean.abcde, "read #" + i);
        }
    }

    @Test
    public void testKnownNamesMatch() throws Exception
    {
        final SmileFactory f = new SmileFactory();
        final List<String> names = _knownNames();
        for (PropertyNameMatcher matcher : _matchers(f, names)) {
            for (int i = 0; i < names.size(); ++i) {
                final byte[] name = names.get(i).getBytes(StandardCharsets.UTF_8);
                // twice, second time with symbol table hits
                for (int round = 0; round < 2; ++round) {
                    for (JsonParser p : _parsers(f, _mapDoc(_name(1), name))) {
                        assertEquals(i, _secondMatch(p, matcher));
                    }
                }
                assertEquals(i, _match(f, matcher, name));
            }
        }
    }

    // Without canonicalization, only a quad match (not decoding and matching
    // by String) yields name instance from matcher: verifies that parser and
    // matcher agree on padding
    @Test
    public void testKnownNamesMatchByQuads() throws Exception
    {
        final SmileFactory f = SmileFactory.builder()
                .disable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                .build();
        final List<String> names = _knownNames();
        for (PropertyNameMatcher matcher : _matchers(f, names)) {
            for (int i = 0; i < names.size(); ++i) {
                final byte[] name = names.get(i).getBytes(StandardCharsets.UTF_8);
                for (JsonParser p : _parsers(f, _mapDoc(_name(1), name))) {
                    try (JsonParser p2 = p) {
                        assertToken(JsonToken.START_OBJECT, p2.nextToken());
                        assertEquals(0, p2.nextNameMatch(matcher));
                        assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, p2.nextNameMatch(matcher));
                        assertEquals(i, p2.nextNameMatch(matcher));
                        assertSame(matcher.nameLookup()[i], p2.currentName(),
                                "Name of "+name.length+" bytes not matched by quads");
                    }
                }
            }
        }
    }

    @Test
    public void testNulPrefixedNamesDoNotMatch() throws Exception
    {
        final SmileFactory f = new SmileFactory();
        final List<String> names = _knownNames();
        for (PropertyNameMatcher matcher : _matchers(f, names)) {
            for (String known : names) {
                final byte[] name = known.getBytes(StandardCharsets.UTF_8);
                for (int nuls = 1; nuls <= 3; ++nuls) {
                    final byte[] withNuls = _insertAtLastQuad(name, (byte) 0, nuls);
                    final String exp = new String(withNuls, StandardCharsets.UTF_8);
                    for (int round = 0; round < 2; ++round) {
                        for (JsonParser p : _parsers(f, _mapDoc(_name(1), withNuls))) {
                            assertEquals(PropertyNameMatcher.MATCH_UNKNOWN_NAME,
                                    _secondMatch(p, matcher), "Name '"+exp+"'");
                            assertEquals(exp, p.currentName());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testFFPrefixedNamesFail() throws Exception
    {
        final SmileFactory f = new SmileFactory();
        final List<String> names = _knownNames();
        for (PropertyNameMatcher matcher : _matchers(f, names)) {
            for (String known : names) {
                final byte[] name = known.getBytes(StandardCharsets.UTF_8);
                final byte[] withFF = _insertAtLastQuad(name, (byte) 0xFF, 1);
                for (JsonParser p : _parsers(f, _mapDoc(_name(1), withFF))) {
                    try {
                        int match = _secondMatch(p, matcher);
                        fail("Should have failed for name of "+withFF.length
                                +" bytes; got match "+match);
                    } catch (StreamReadException e) {
                        verifyException(e, "Invalid UTF-8", "Invalid byte 0xFF");
                    }
                }
            }
        }
    }

    private List<PropertyNameMatcher> _matchers(SmileFactory f, List<String> names) {
        List<Named> named = new ArrayList<>();
        for (String name : names) {
            named.add(Named.fromString(name));
        }
        List<PropertyNameMatcher> result = new ArrayList<>();
        result.add(f.constructNameMatcher(named, false));
        result.add(f.constructCINameMatcher(named, false, Locale.ROOT));
        return result;
    }

    // ASCII names of all lengths, plus one non-ASCII name
    private List<String> _knownNames() {
        List<String> names = new ArrayList<>();
        for (int len = 1; len <= MAX_NAME_LEN; ++len) {
            names.add(new String(_name(len), StandardCharsets.UTF_8));
        }
        names.add(UNICODE_NAME);
        return names;
    }

    private List<JsonParser> _parsers(SmileFactory f, byte[] doc) {
        List<JsonParser> result = new ArrayList<>();
        result.add(f.createParser(ObjectReadContext.empty(), doc));
        result.add(f.createParser(ObjectReadContext.empty(), new ByteArrayInputStream(doc)));
        return result;
    }

    private int _match(SmileFactory f, PropertyNameMatcher matcher, byte[] name) throws Exception {
        try (JsonParser p = f.createParser(ObjectReadContext.empty(), _mapDoc(name))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            return p.nextNameMatch(matcher);
        }
    }

    // Match of second property name (first one read via `nextNameMatch()` too)
    private int _secondMatch(JsonParser p, PropertyNameMatcher matcher) throws Exception {
        try (JsonParser p2 = p) {
            assertToken(JsonToken.START_OBJECT, p2.nextToken());
            assertEquals(0, p2.nextNameMatch(matcher));
            assertEquals(PropertyNameMatcher.MATCH_ODD_TOKEN, p2.nextNameMatch(matcher));
            return p2.nextNameMatch(matcher);
        }
    }

    // ASCII name of given length
    private static byte[] _name(int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; ++i) {
            b[i] = (byte) ('a' + (i % 26));
        }
        return b;
    }

    // Name with `count` copies of byte `b` inserted at start of its last quad
    private static byte[] _insertAtLastQuad(byte[] name, byte b, int count) {
        final int len = name.length;
        final int lastQuadStart = ((len - 1) >> 2) << 2;
        byte[] result = new byte[len + count];
        System.arraycopy(name, 0, result, 0, lastQuadStart);
        for (int i = 0; i < count; ++i) {
            result[lastQuadStart + i] = b;
        }
        System.arraycopy(name, lastQuadStart, result, lastQuadStart + count, len - lastQuadStart);
        return result;
    }

    // Object with given (raw) names as short name tokens, small int values
    private static byte[] _mapDoc(byte[]... names) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SmileConstants.HEADER_BYTE_1);
        out.write(SmileConstants.HEADER_BYTE_2);
        out.write(SmileConstants.HEADER_BYTE_3);
        out.write(0);
        out.write(SmileConstants.TOKEN_LITERAL_START_OBJECT);
        for (int i = 0; i < names.length; ++i) {
            final byte[] name = names[i];
            if (_isAscii(name)) {
                out.write(SmileConstants.TOKEN_PREFIX_KEY_ASCII + name.length - 1);
            } else {
                out.write(SmileConstants.TOKEN_PREFIX_KEY_UNICODE + name.length - 2);
            }
            out.write(name, 0, name.length);
            out.write(SmileConstants.TOKEN_PREFIX_SMALL_INT + (i << 1)); // zigzag
        }
        out.write(SmileConstants.TOKEN_LITERAL_END_OBJECT);
        return out.toByteArray();
    }

    private static boolean _isAscii(byte[] name) {
        for (byte b : name) {
            if (b < 0) {
                return false;
            }
        }
        return true;
    }
}
