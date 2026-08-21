package tools.jackson.dataformat.cbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.sym.SimpleNameMatcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for [dataformats-binary#736]: Object property names too long to fit in the
 * input buffer are decoded by {@code CBORParser._finishLongText()}, which -- since
 * [dataformats-binary#733] made it add what it decodes to the "stringref" reference
 * table -- registered the name a second time, on top of the registration the name
 * paths already do. A single name then consumed two indexes, so every following
 * reference resolved to the wrong String.
 *<p>
 * Only reproduces when reading from an {@link java.io.InputStream}: with
 * {@code byte[]} input the whole document is already buffered, so
 * {@code _decodeLongerName()} never delegates to {@code _finishLongText()}.
 */
public class StringRef736Test extends CBORTestBase
{
    // Longer than the default 8000 byte input buffer, but below the default
    // 50000 byte `maxNameLength`, so this is reachable with stock settings
    private final static int LONG_NAME_LENGTH = 9000;

    private final static int MODE_NEXT_TOKEN = 1;
    private final static int MODE_NEXT_NAME = 2;
    private final static int MODE_NEXT_NAME_MATCH = 3;
    // 3.x only: `nextNameMatch()` decodes long names via `_nextNameLong()`, which
    //   does its own "stringref" registration and so could double up as well
    private final static int MODE_NEXT_NAME_MATCHER = 4;

    private final static PropertyNameMatcher NAME_MATCHER
        = SimpleNameMatcher.construct(Locale.ROOT, Arrays.asList("zzz"));

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    // Long name is entry #0 and "AAA" entry #1, so reference #1 must be "AAA".
    // If the name is registered twice it becomes #0 AND #1, and #1 wrongly
    // resolves to the name
    @Test
    public void testLongNameDoesNotConsumeTwoIndexes() throws Exception
    {
        final String longName = generateAsciiString(LONG_NAME_LENGTH);
        final byte[] doc = _longNameDoc(longName, 1);

        _verifyAllModes(doc, longName, "AAA");
    }

    // Conversely, the name must still be registered exactly once: reference #0
    // resolves to the name itself
    @Test
    public void testLongNameStillReferencedOnce() throws Exception
    {
        final String longName = generateAsciiString(LONG_NAME_LENGTH);
        final byte[] doc = _longNameDoc(longName, 0);

        _verifyAllModes(doc, longName, longName);
    }

    // Same, with a name needing real UTF-8 decoding, since the name is decoded
    // by a different code path than the ASCII-only case
    @Test
    public void testLongUnicodeNameDoesNotConsumeTwoIndexes() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < LONG_NAME_LENGTH) {
            sb.append("Beyoncé über 中文 ");
        }
        final String longName = sb.toString();
        final byte[] doc = _longNameDoc(longName, 1);

        _verifyAllModes(doc, longName, "AAA");
    }

    // String VALUES that go through `_finishLongText()` must still be registered:
    // verifies the fix did not disable reference tracking for values too
    @Test
    public void testLongValueStillReferenced() throws Exception
    {
        final String longValue = generateAsciiString(LONG_NAME_LENGTH);

        // tag(256) [ <longValue>, "AAA", tag(25) 0 ] => #0 is longValue
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0xD9); b.write(0x01); b.write(0x00);
        b.write(0x83);                                  // Array, 3 elements
        _writeText(b, utf8Bytes(longValue));
        b.write(0x63); b.write('A'); b.write('A'); b.write('A');
        b.write(0xD8); b.write(0x19); b.write(0x00);    // stringref #0
        final byte[] doc = b.toByteArray();

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(doc, stream)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(longValue, p.getString());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("AAA", p.getString());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(longValue, p.getString(),
                        "Reference to long value, stream = "+stream);
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    /*
    /**********************************************************
    /* Helper methods, document construction
    /**********************************************************
     */

    /**
     * Builds document
     *<pre>
     *   tag(256) [ { &lt;longName&gt; : "AAA" }, tag(25) refIndex ]
     *</pre>
     * where a conformant encoder assigns {@code longName} index #0 and
     * {@code "AAA"} index #1.
     */
    private byte[] _longNameDoc(String longName, int refIndex) throws Exception
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0xD9); b.write(0x01); b.write(0x00); // tag 256, "stringref-namespace"
        b.write(0x82);                               // Array, 2 elements
        b.write(0xA1);                               // Object, 1 entry
        _writeText(b, utf8Bytes(longName));
        b.write(0x63); b.write('A'); b.write('A'); b.write('A');
        b.write(0xD8); b.write(0x19); b.write(refIndex); // tag 25, "stringref"
        return b.toByteArray();
    }

    /**
     * Writes text using the <i>minimal</i> (canonical) length prefix, the way a
     * real encoder would: what is under test here is which entries take up a
     * reference index, not how lengths are encoded. Non-minimal prefixes are
     * {@code StringRef735Test}'s subject.
     */
    private void _writeText(ByteArrayOutputStream b, byte[] raw) {
        final int len = raw.length;
        if (len <= 23) {                    // length in the type byte itself
            b.write(0x60 + len);
        } else if (len <= 0xFF) {           // 1-byte length suffix
            b.write(0x78);
            b.write(len);
        } else if (len <= 0xFFFF) {         // 2-byte length suffix
            b.write(0x79);
            b.write(len >> 8);
            b.write(len & 0xFF);
        } else {                            // 4-byte length suffix
            b.write(0x7A);
            b.write(len >> 24);
            b.write((len >> 16) & 0xFF);
            b.write((len >> 8) & 0xFF);
            b.write(len & 0xFF);
        }
        b.write(raw, 0, len);
    }

    /*
    /**********************************************************
    /* Helper methods, verification
    /**********************************************************
     */

    private void _verifyAllModes(byte[] doc, String expName, String expRef)
    {
        assertAll(
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_TOKEN),
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_NAME),
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_NAME_MATCH),
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_NAME_MATCHER));
    }

    private void _verifyRef(byte[] doc, String expName, String expRef, int mode)
        throws Exception
    {
        // `byte[]` input keeps the whole name buffered and is the control case;
        // only `InputStream` input reaches `_finishLongText()`
        for (boolean stream : new boolean[] { false, true }) {
            final String desc = "(mode: "+mode+", stream: "+stream+")";
            try (JsonParser p = _parser(doc, stream)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                _advanceToName(p, expName, mode);
                assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
                assertEquals(expName, p.currentName(), desc);
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("AAA", p.getString(), desc);
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(expRef, p.getString(), desc);
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    private void _advanceToName(JsonParser p, String expName, int mode)
        throws Exception
    {
        switch (mode) {
        case MODE_NEXT_TOKEN:
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            break;
        case MODE_NEXT_NAME:
            assertEquals(expName, p.nextName());
            break;
        case MODE_NEXT_NAME_MATCH:
            // Will not match (names this long never take the fast path), but
            // the name still gets decoded, which is what matters here
            assertFalse(p.nextName(new SerializedString("zzz")));
            break;
        default:
            // Likewise no match expected; exercises `_nextNameLong()`
            p.nextNameMatch(NAME_MATCHER);
            break;
        }
    }

    private JsonParser _parser(byte[] doc, boolean stream) throws Exception {
        return stream ? cborParser(new ByteArrayInputStream(doc)) : cborParser(doc);
    }
}
