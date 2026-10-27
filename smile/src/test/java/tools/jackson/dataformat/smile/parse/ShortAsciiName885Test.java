package tools.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#885]: "short ASCII" property names with non-ASCII bytes
// must be rejected, regardless of byte position, code path or configuration
public class ShortAsciiName885Test extends BaseTestForSmile
{
    static class EAcute {
        @JsonProperty("é")
        public int value;
    }

    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    // Bytes fed per call to non-blocking parser; 0 for blocking from byte[],
    // -1 for blocking from InputStream
    private final static int[] MODES = { 0, -1, 1, 3, 1000 };

    private final SmileFactory CANON_F = new SmileFactory();
    private final SmileFactory NO_CANON_F = SmileFactory.builder()
            .disable(JsonFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
            .build();

    // Non-ASCII byte at every position of names of every short ASCII length
    @Test
    public void testNonAsciiAtAnyPosition() throws Exception
    {
        for (int len = 1; len <= 64; ++len) {
            for (int pos = 0; pos < len; ++pos) {
                for (int b : new int[] { 0x80, 0xFF }) {
                    final byte[] name = _name(len);
                    name[pos] = (byte) b;
                    final byte[] doc = _doc(_asciiName(name));
                    for (SmileFactory f : new SmileFactory[] { CANON_F, NO_CANON_F }) {
                        for (int mode : MODES) {
                            _verifyFails(f, doc, mode, b);
                        }
                    }
                }
            }
        }
    }

    // Short ASCII name must not get into symbol table, to be matched by a later
    // (and otherwise invalid) short Unicode name with same bytes
    @Test
    public void testNoValidationBypass() throws Exception
    {
        final byte[] name = { 0x61, (byte) 0xFE };
        for (int mode : MODES) {
            _verifyFails(CANON_F, _doc(_asciiName(name), _unicodeName(name)), mode, 0xFE);
            // and in separate documents, sharing symbol table
            final SmileFactory f = new SmileFactory();
            _verifyFails(f, _doc(_asciiName(name)), mode, 0xFE);
            try {
                String names = _names(f, _doc(_unicodeName(name)), mode);
                fail("Should have failed, mode "+mode+"; got names: "+names);
            } catch (StreamReadException e) {
                verifyException(e, "Invalid byte 0xFE in short Unicode text block");
            }
        }
    }

    // Short ASCII name must not get into symbol table, to be returned for a later
    // valid short Unicode name with same bytes
    @Test
    public void testNoWrongNameForValidInput() throws Exception
    {
        final byte[] name = { (byte) 0xC3, (byte) 0xA9 };
        for (int mode : MODES) {
            final SmileFactory f = new SmileFactory();
            _verifyFails(f, _doc(_asciiName(name)), mode, 0xC3);
            assertEquals("é", _names(f, _doc(_unicodeName(name)), mode));
        }
    }

    // `nextName(SerializableString)` must not match non-ASCII expected name
    // against short ASCII name with same bytes
    @Test
    public void testNextNameWithSerializableString() throws Exception
    {
        final byte[] doc = _doc(_asciiName(new byte[] { (byte) 0xC3, (byte) 0xA9 }));
        for (SmileFactory f : new SmileFactory[] { CANON_F, NO_CANON_F }) {
            try (JsonParser p = new SmileMapper(f).createParser(doc)) {
                assertEquals(JsonToken.START_OBJECT, p.nextToken());
                p.nextName(new SerializedString("é"));
                fail("Should have failed; got name: "+p.currentName());
            } catch (StreamReadException e) {
                verifyException(e, "Invalid byte 0xC3 in short ASCII property name");
            }
        }
    }

    // `nextName(SerializableString)` must compare against raw, not JSON-escaped,
    // bytes of expected name
    @Test
    public void testNextNameWithEscapedChars() throws Exception
    {
        // Short Unicode name `é\"` (literal backslash) must not match expected `é"`,
        // even though its bytes equal the JSON-escaped form of the latter
        _verifyNextName(_doc(_unicodeName(utf8Bytes("\u00E9\\\""))),
                "\u00E9\"", false, "\u00E9\\\"");
        // Same for short ASCII names
        _verifyNextName(_doc(_asciiName(utf8Bytes("a\\\"b"))),
                "a\"b", false, "a\\\"b");
        // And names with chars that need escaping must still match
        _verifyNextName(_doc(_asciiName(utf8Bytes("a\"b"))),
                "a\"b", true, "a\"b");
        _verifyNextName(_doc(_unicodeName(utf8Bytes("\u00E9\"\n"))),
                "\u00E9\"\n", true, "\u00E9\"\n");
    }

    private void _verifyNextName(byte[] doc, String expName, boolean expMatch,
            String actualName) throws Exception
    {
        for (SmileFactory f : new SmileFactory[] { CANON_F, NO_CANON_F }) {
            try (JsonParser p = new SmileMapper(f).createParser(doc)) {
                assertEquals(JsonToken.START_OBJECT, p.nextToken());
                assertEquals(expMatch, p.nextName(new SerializedString(expName)));
                assertEquals(JsonToken.PROPERTY_NAME, p.currentToken());
                assertEquals(actualName, p.currentName());
            }
        }
    }

    // `nextNameMatch()` (used by POJO deserialization) must not match either
    @Test
    public void testNextNameMatch() throws Exception
    {
        final byte[] doc = _doc(_asciiName(new byte[] { (byte) 0xC3, (byte) 0xA9 }));
        for (SmileFactory f : new SmileFactory[] { CANON_F, NO_CANON_F }) {
            try {
                EAcute result = new SmileMapper(f).readValue(doc, EAcute.class);
                fail("Should have failed; got value: "+result.value);
            } catch (StreamReadException e) {
                verifyException(e, "Invalid byte 0xC3 in short ASCII property name");
            }
        }
    }

    private void _verifyFails(SmileFactory f, byte[] doc, int mode, int b) throws Exception
    {
        try {
            String names = _names(f, doc, mode);
            fail("Should have failed for mode "+mode+"; got names: "+names);
        } catch (StreamReadException e) {
            verifyException(e, String.format("Invalid byte 0x%02X in short ASCII property name", b));
        }
    }

    // Reads all property names, comma-separated
    private String _names(SmileFactory f, byte[] doc, int mode) throws Exception
    {
        final StringBuilder sb = new StringBuilder();
        final ObjectReader r = new SmileMapper(f).reader();
        if (mode <= 0) {
            try (JsonParser p = (mode == 0) ? r.createParser(doc)
                    : r.createParser(new ByteArrayInputStream(doc))) {
                JsonToken t;
                while ((t = p.nextToken()) != null) {
                    if (t == JsonToken.PROPERTY_NAME) {
                        _append(sb, p.currentName());
                    }
                }
            }
            return sb.toString();
        }
        try (JsonParser p = r.createNonBlockingByteArrayParser()) {
            final ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
            int offset = 0;
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t == JsonToken.NOT_AVAILABLE) {
                    if (offset < doc.length) {
                        int end = Math.min(doc.length, offset + mode);
                        feeder.feedInput(doc, offset, end);
                        offset = end;
                    } else {
                        feeder.endOfInput();
                    }
                } else if (t == JsonToken.PROPERTY_NAME) {
                    _append(sb, p.currentName());
                }
            }
        }
        return sb.toString();
    }

    private static void _append(StringBuilder sb, String name) {
        if (sb.length() > 0) {
            sb.append(',');
        }
        sb.append(name);
    }

    // ASCII name of given length
    private static byte[] _name(int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; ++i) {
            b[i] = (byte) ('a' + (i % 26));
        }
        return b;
    }

    // Short ASCII name token (1 - 64 bytes)
    private static byte[] _asciiName(byte[] name) {
        return _token(SmileConstants.TOKEN_PREFIX_KEY_ASCII + name.length - 1, name);
    }

    // Short Unicode name token (2 - 57 bytes)
    private static byte[] _unicodeName(byte[] name) {
        return _token(SmileConstants.TOKEN_PREFIX_KEY_UNICODE + name.length - 2, name);
    }

    private static byte[] _token(int typeByte, byte[] name) {
        byte[] b = new byte[name.length + 1];
        b[0] = (byte) typeByte;
        System.arraycopy(name, 0, b, 1, name.length);
        return b;
    }

    // Object with given property name tokens (with small int values)
    private static byte[] _doc(byte[]... nameTokens) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER, 0, HEADER.length);
        out.write(SmileConstants.TOKEN_LITERAL_START_OBJECT);
        for (byte[] token : nameTokens) {
            out.write(token, 0, token.length);
            out.write(0xC2); // small int 1
        }
        out.write(SmileConstants.TOKEN_LITERAL_END_OBJECT);
        return out.toByteArray();
    }
}
