package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for [dataformats-binary#735]: the Object property name paths used to pass
 * the 5-bit length marker, instead of the name's actual byte length, to
 * {@code shouldReferenceString()}. Markers 24 - 27 ("1/2/4/8-byte length suffix
 * follows") are all above every minimum-length threshold, so a name shorter than
 * the threshold got registered in the "stringref" table when a conformant encoder
 * would have skipped it -- shifting all following reference indexes by one.
 *<p>
 * Only reachable for non-canonically encoded length prefixes: canonical marker 24
 * implies length &gt;= 24, marker 25 length &gt;= 256, and so on, all above the
 * thresholds. Jackson's own generator always writes minimal prefixes, so these
 * documents are hand-crafted.
 */
public class StringRef735Test extends CBORTestBase
{
    // How the name is to be read. Modes 1 and 2 exercise the two paths that
    // passed `lenMarker`; mode 3's fast path always used the real byte length,
    // and is included to keep it that way (it only reaches the shared paths for
    // markers above 24, which it does not handle itself)
    private final static int MODE_NEXT_TOKEN = 1;
    private final static int MODE_NEXT_FIELD_NAME = 2;
    private final static int MODE_NEXT_FIELD_NAME_MATCH = 3;

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    // 2-byte name written with a 1-byte length suffix (marker 24): below the
    // 3-byte minimum for index #0, so a conformant encoder does NOT give it an
    // index -- making "AAA" entry #0, and reference #0 resolve to "AAA"
    @Test
    public void testShortNameWithLongMarkerNotReferenced() throws Exception
    {
        final String name = "ab";
        final byte[] doc = _doc(name, _nonCanonical1ByteLen(name));

        _verifyAllModes(doc, name, "AAA");
    }

    // Same, but with a 2-byte length suffix (marker 25) for a 2-byte name
    @Test
    public void testShortNameWith2ByteMarkerNotReferenced() throws Exception
    {
        final String name = "ab";
        final byte[] doc = _doc(name, _nonCanonical2ByteLen(name));

        _verifyAllModes(doc, name, "AAA");
    }

    // Conversely: a name that IS long enough must still be registered. Here the
    // name is entry #0 and "AAA" entry #1, so reference #0 is the name itself --
    // verifies the fix did not simply stop registering names
    @Test
    public void testLongEnoughNameStillReferenced() throws Exception
    {
        final String name = generateAsciiString(30); // canonical marker 24
        final byte[] doc = _doc(name, _nonCanonical1ByteLen(name));

        _verifyAllModes(doc, name, name);
    }

    // Exactly at the 3-byte threshold for index #0: registered either way, but
    // worth pinning since it is the boundary the marker value used to mask
    @Test
    public void testNameAtThresholdReferenced() throws Exception
    {
        final String name = "abc";
        final byte[] doc = _doc(name, _nonCanonical1ByteLen(name));

        _verifyAllModes(doc, name, name);
    }

    // Chunked (indefinite length) names are never referenced, no matter how long
    // they are: the decoded length is not known when the marker is read, and a
    // conformant encoder only indexes definite-length strings. So "AAA" is entry
    // #0 here even though the name is 4 bytes
    @Test
    public void testChunkedNameNotReferenced() throws Exception
    {
        final byte[] doc = _chunkedNameDoc("ab", "cd");

        _verifyAllModes(doc, "abcd", "AAA");
    }

    /*
    /**********************************************************
    /* Helper methods, document construction
    /**********************************************************
     */

    /**
     * Builds document
     *<pre>
     *   tag(256) [ { &lt;name&gt; : "AAA" }, tag(25) 0 ]
     *</pre>
     * with the property name encoded using the given (possibly non-canonical)
     * length prefix. Reference is always to entry #0, which is what makes the
     * tests sensitive to whether the name took up an index: if it did, #0 is the
     * name, and if it did not, #0 is the {@code "AAA"} value.
     */
    private byte[] _doc(String name, byte[] namePrefix) throws Exception
    {
        final byte[] rawName = utf8Bytes(name);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0xD9); b.write(0x01); b.write(0x00); // tag 256, "stringref-namespace"
        b.write(0x82);                               // Array, 2 elements
        b.write(0xA1);                               // Object, 1 entry
        b.write(namePrefix, 0, namePrefix.length);
        b.write(rawName, 0, rawName.length);
        b.write(0x63); b.write('A'); b.write('A'); b.write('A');
        b.write(0xD8); b.write(0x19); b.write(0x00); // tag 25, "stringref" to entry #0
        return b.toByteArray();
    }

    /**
     * Same shape as {@link #_doc}, but with the property name written as chunked
     * (indefinite length) text made up of the given chunks.
     */
    private byte[] _chunkedNameDoc(String... chunks) throws Exception
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0xD9); b.write(0x01); b.write(0x00); // tag 256, "stringref-namespace"
        b.write(0x82);                               // Array, 2 elements
        b.write(0xA1);                               // Object, 1 entry
        b.write(0x7F);                               // text, indefinite length
        for (String chunk : chunks) {
            final byte[] raw = utf8Bytes(chunk);
            b.write(0x60 + raw.length);              // text, length in type byte
            b.write(raw, 0, raw.length);
        }
        b.write(0xFF);                               // break
        b.write(0x63); b.write('A'); b.write('A'); b.write('A');
        b.write(0xD8); b.write(0x19); b.write(0x00); // tag 25, "stringref" to entry #0
        return b.toByteArray();
    }

    // Length prefix using marker 24, "1-byte length suffix follows"
    private byte[] _nonCanonical1ByteLen(String name) {
        final int len = utf8Bytes(name).length;
        return new byte[] { (byte) 0x78, (byte) len };
    }

    // Length prefix using marker 25, "2-byte length suffix follows"
    private byte[] _nonCanonical2ByteLen(String name) {
        final int len = utf8Bytes(name).length;
        return new byte[] { (byte) 0x79, (byte) (len >> 8), (byte) len };
    }

    /*
    /**********************************************************
    /* Helper methods, verification
    /**********************************************************
     */

    // Runs every read mode, reporting all failures: the two name-decoding paths
    // (`_decodePropertyName()` and the one inlined in `nextFieldName()`) had
    // separate copies of the faulty check, so each needs its own coverage
    private void _verifyAllModes(byte[] doc, String expName, String expRef)
    {
        assertAll(
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_TOKEN),
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_FIELD_NAME),
                () -> _verifyRef(doc, expName, expRef, MODE_NEXT_FIELD_NAME_MATCH));
    }

    private void _verifyRef(byte[] doc, String expName, String expRef, int mode)
        throws Exception
    {
        final String desc = "(mode: "+mode+")";
        try (JsonParser p = cborParser(doc)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            _advanceToName(p, expName, mode);
            assertToken(JsonToken.FIELD_NAME, p.currentToken());
            assertEquals(expName, p.currentName(), desc);
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("AAA", p.getText(), desc);
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(expRef, p.getText(), desc);
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    private void _advanceToName(JsonParser p, String expName, int mode)
        throws Exception
    {
        switch (mode) {
        case MODE_NEXT_TOKEN:
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            break;
        case MODE_NEXT_FIELD_NAME:
            assertEquals(expName, p.nextFieldName());
            break;
        case MODE_NEXT_FIELD_NAME_MATCH:
            assertTrue(p.nextFieldName(new SerializedString(expName)),
                    "Should match name '"+expName+"'");
            break;
        default:
            fail("Unknown mode: "+mode);
        }
    }
}
