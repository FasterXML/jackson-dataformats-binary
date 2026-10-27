package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORParser;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#868]: name with 0xFF byte at start of last quad must not
// match a shorter name already in the symbol table
public class PropertyName0xFF868Test extends CBORTestBase
{
    private final static int MAX_NAME_LEN = 25;

    private final static int MODE_NEXT_TOKEN = 0;
    private final static int MODE_NEXT_NAME = 1;
    private final static int MODE_NEXT_NAME_SERIALIZABLE = 2;
    private final static int MODE_NEXT_NAME_MATCH = 3;

    private final static int[] ALL_MODES = new int[] {
            MODE_NEXT_TOKEN, MODE_NEXT_NAME, MODE_NEXT_NAME_SERIALIZABLE, MODE_NEXT_NAME_MATCH
    };

    @Test
    public void testSameDocument() throws Exception
    {
        for (int mode : ALL_MODES) {
            for (int len = 1; len <= MAX_NAME_LEN; ++len) {
                final byte[] bad = _nameWith0xFF(len);
                if (bad == null) {
                    continue;
                }
                final byte[] doc = _mapDoc(_name(len), bad);
                _verifyFails(cborParser(doc), mode, len);
                _verifyFails(cborParser(new ByteArrayInputStream(doc)), mode, len);
            }
        }
    }

    // Without canonicalization names are always fully decoded: must fail
    // the same way as with symbol table lookup
    @Test
    public void testNonCanonical() throws Exception
    {
        final CBORFactory f = CBORFactory.builder()
                .disable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                .build();
        for (int mode : ALL_MODES) {
            for (int len = 1; len <= MAX_NAME_LEN; ++len) {
                final byte[] bad = _nameWith0xFF(len);
                if (bad == null) {
                    continue;
                }
                final byte[] doc = _mapDoc(_name(len), bad);
                _verifyFails(cborParser(f, doc), mode, len);
                _verifyFails(cborParser(f, new ByteArrayInputStream(doc)), mode, len);
            }
        }
    }

    // Shorter name only in the shared root symbol table, added by an earlier
    // parser: with the 0xFF check in place lookup fails before consulting the
    // table, but guards against regressions specific to root table merging
    @Test
    public void testNameFromEarlierDocument() throws Exception
    {
        for (int mode : ALL_MODES) {
            for (int len = 1; len <= MAX_NAME_LEN; ++len) {
                final byte[] bad = _nameWith0xFF(len);
                if (bad == null) {
                    continue;
                }
                final CBORFactory f = cborFactory();
                try (CBORParser p = cborParser(f, _mapDoc(_name(len)))) {
                    assertToken(JsonToken.START_OBJECT, p.nextToken());
                    assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
                    assertEquals(new String(_name(len), StandardCharsets.UTF_8),
                            p.currentName());
                    assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                    assertToken(JsonToken.END_OBJECT, p.nextToken());
                }
                _verifyFails(cborParser(f, _mapDoc(bad)), mode, len);
            }
        }
    }

    private void _verifyFails(CBORParser p, int mode, int len) throws Exception
    {
        // expected name for `nextName(SerializableString)` and `nextNameMatch()`:
        // must NOT match either name, since a match goes through fast path that
        // bypasses the symbol table (and so would not exercise the bug)
        final SerializableString expName = new SerializedString("not-matching");
        final PropertyNameMatcher matcher = SimpleNameMatcher.construct(Locale.ROOT,
                Collections.singletonList(expName.getValue()));
        final StringBuilder names = new StringBuilder();
        try (CBORParser parser = p) {
            assertToken(JsonToken.START_OBJECT, parser.nextToken());
            while (true) {
                JsonToken t;
                switch (mode) {
                case MODE_NEXT_NAME:
                    t = (parser.nextName() != null) ? JsonToken.PROPERTY_NAME
                            : parser.currentToken();
                    break;
                case MODE_NEXT_NAME_SERIALIZABLE:
                    parser.nextName(expName);
                    t = parser.currentToken();
                    break;
                case MODE_NEXT_NAME_MATCH:
                    parser.nextNameMatch(matcher);
                    t = parser.currentToken();
                    break;
                default:
                    t = parser.nextToken();
                }
                if (t == JsonToken.END_OBJECT) {
                    break;
                }
                assertToken(JsonToken.PROPERTY_NAME, t);
                names.append(" '").append(parser.currentName()).append("'");
                assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            }
            fail("Should have failed for name length "+(len+1)+" (mode "+mode
                    +"), got names:"+names);
        } catch (StreamReadException e) {
            verifyException(e, "Invalid UTF-8 byte 0xff in Object property name");
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

    // Name one byte longer than `_name(len)`, with 0xFF inserted at start of
    // its last quad, so that its quads equal those of `_name(len)` once padded;
    // or null if lengths differ in quad count
    private static byte[] _nameWith0xFF(int len) {
        if ((len & 3) == 0) {
            return null;
        }
        final byte[] name = _name(len);
        final int lastQuadStart = (len >> 2) << 2;
        byte[] b = new byte[len+1];
        System.arraycopy(name, 0, b, 0, lastQuadStart);
        b[lastQuadStart] = (byte) 0xFF;
        System.arraycopy(name, lastQuadStart, b, lastQuadStart+1, len - lastQuadStart);
        return b;
    }

    private static byte[] _mapDoc(byte[]... names) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA0 | names.length);
        int value = 1;
        for (byte[] name : names) {
            if (name.length < 24) {
                out.write(0x60 | name.length);
            } else {
                out.write(0x78);
                out.write(name.length);
            }
            out.write(name, 0, name.length);
            out.write(value++);
        }
        return out.toByteArray();
    }
}
