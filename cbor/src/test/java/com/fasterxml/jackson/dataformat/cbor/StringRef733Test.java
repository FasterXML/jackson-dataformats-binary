package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

/**
 * Tests for [dataformats-binary#733]: Strings decoded by
 * {@code CBORParser._finishLongText()} -- that is, Strings that do not fit
 * in the input buffer, only possible when reading from an {@link java.io.InputStream}
 * -- were not added to the String reference table. Since reference indexes are
 * positional, that made the parser's table disagree with the encoder's, so that
 * following references either failed with "String reference out of range" or,
 * worse, silently resolved to the wrong String.
 */
public class StringRef733Test extends CBORTestBase
{
    // Longer than the default 8000 byte input buffer, to force use of
    // `_finishLongText()` when reading from an `InputStream`
    private final static int LONG_LENGTH = 9000;

    /*
    /**********************************************************
    /* Test methods, hand-crafted documents
    /**********************************************************
     */

    // Long String is entry #0, so reference #1 must resolve to "AAA"
    public void testLongStringDoesNotShiftFollowingRefs() throws Exception
    {
        final String longString = _generateAscii(LONG_LENGTH);
        final byte[] doc = _stringRefDoc(longString, 1);

        _verifyRefResolvesTo(_parser(doc, false), longString, "AAA");
        _verifyRefResolvesTo(_parser(doc, true), longString, "AAA");
    }

    // Reference #0 is the long String itself: before fix, failed with
    // "String reference (0) out of range" since table was left empty
    public void testReferenceToLongStringItself() throws Exception
    {
        final String longString = _generateAscii(LONG_LENGTH);
        final byte[] doc = _stringRefDoc(longString, 0);

        _verifyRefResolvesTo(_parser(doc, false), longString, longString);
        _verifyRefResolvesTo(_parser(doc, true), longString, longString);
    }

    // Same, but with a String that needs actual UTF-8 decoding (not just the
    // ASCII path), to verify multi-byte characters do not throw the count off
    public void testReferenceToLongUnicodeString() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < LONG_LENGTH) {
            sb.append("Beyoncé über 中文 ");
        }
        final String longString = sb.toString();
        final byte[] doc = _stringRefDoc(longString, 0);

        _verifyRefResolvesTo(_parser(doc, false), longString, longString);
        _verifyRefResolvesTo(_parser(doc, true), longString, longString);
    }

    /*
    /**********************************************************
    /* Test methods, generator round-trip
    /**********************************************************
     */

    // Round-trip through `CBORGenerator` with STRINGREF enabled. Needs to use
    // `SerializableString` (or `writeRawUTF8String()`): plain `writeString()`
    // chunks Strings this long, see `testLongChunkedStringNotReferenced()`
    public void testLongStringRoundTrip() throws Exception
    {
        final String longString = _generateAscii(LONG_LENGTH);
        final SerializedString longSerialized = new SerializedString(longString);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CBORGenerator gen = stringrefCborGenerator(bytes)) {
            gen.writeStartArray();
            gen.writeString(longSerialized);
            gen.writeString("AAA");
            // Written as reference to entry #0:
            gen.writeString(longSerialized);
            // ... and this one as reference to entry #1:
            gen.writeString("AAA");
            gen.writeEndArray();
        }
        final byte[] doc = bytes.toByteArray();

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(doc, stream)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(longString, p.getText());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("AAA", p.getText());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("Reference to long String, stream = "+stream,
                        longString, p.getText());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("Reference to short String, stream = "+stream,
                        "AAA", p.getText());
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Conversely: chunked (indefinite length) Strings must NOT be added to the
    // reference table, by either side. Generator chunks Strings longer than
    // ~4000 characters when written via `writeString(String)`, so the long
    // String below does not take up an index -- and "AAA" is entry #0
    public void testLongChunkedStringNotReferenced() throws Exception
    {
        final String longString = _generateAscii(LONG_LENGTH);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CBORGenerator gen = stringrefCborGenerator(bytes)) {
            gen.writeStartArray();
            gen.writeString(longString);
            gen.writeString("AAA");
            // Written as reference to entry #0, that is, "AAA":
            gen.writeString("AAA");
            gen.writeEndArray();
        }
        final byte[] doc = bytes.toByteArray();

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(doc, stream)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(longString, p.getText());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("AAA", p.getText());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("Reference to short String, stream = "+stream,
                        "AAA", p.getText());
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    /**
     * Constructs document
     *<pre>
     *   tag(256) [ &lt;longString&gt;, "AAA", "BBB", tag(25) refIndex ]
     *</pre>
     * where encoder-side reference table is {@code longString} = #0,
     * {@code "AAA"} = #1, {@code "BBB"} = #2.
     */
    private byte[] _stringRefDoc(String longString, int refIndex) throws Exception
    {
        final byte[] rawString = utf8Bytes(longString);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0xD9); b.write(0x01); b.write(0x00); // tag 256, "stringref-namespace"
        b.write(0x84); // Array, 4 elements
        // text, 4-byte length (works for any length we use here)
        b.write(0x7A);
        b.write(rawString.length >> 24);
        b.write((rawString.length >> 16) & 0xFF);
        b.write((rawString.length >> 8) & 0xFF);
        b.write(rawString.length & 0xFF);
        b.write(rawString, 0, rawString.length);
        b.write(0x63); b.write('A'); b.write('A'); b.write('A');
        b.write(0x63); b.write('B'); b.write('B'); b.write('B');
        b.write(0xD8); b.write(0x19); b.write(refIndex); // tag 25, "stringref"
        return b.toByteArray();
    }

    // Reads through document produced by `_stringRefDoc()`, verifying that
    // the trailing reference resolves to expected String
    private void _verifyRefResolvesTo(JsonParser p, String longString, String exp)
        throws Exception
    {
        try {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(longString, p.getText());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("AAA", p.getText());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("BBB", p.getText());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(exp, p.getText());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
        } finally {
            p.close();
        }
    }

    // `byte[]`-backed parser decodes long Strings via `_finishShortText()` and
    // was never affected; `InputStream`-backed one uses `_finishLongText()`
    private JsonParser _parser(byte[] doc, boolean stream) throws Exception {
        return stream ? cborParser(new ByteArrayInputStream(doc)) : cborParser(doc);
    }

    private String _generateAscii(int len) {
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len) {
            sb.append((char) ('a' + (sb.length() % 26)));
        }
        return sb.toString();
    }
}
