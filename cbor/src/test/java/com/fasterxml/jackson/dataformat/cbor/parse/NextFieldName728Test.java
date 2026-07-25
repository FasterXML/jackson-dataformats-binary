package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#728]: `nextFieldName(SerializableString)` used to consume
// an entry of expected-length (definite-length) Object twice, when its
// name-matching fast path did not succeed, truncating the Object
public class NextFieldName728Test extends CBORTestBase
{
    // {"a":1, "b":2} as definite-length Object; name not matched
    @Test
    public void testNonMatchingNameDefiniteLength() throws Exception
    {
        final byte[] DOC = twoEntryObject(false);

        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(DOC, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertToken(JsonToken.FIELD_NAME, p.currentToken());
                assertEquals("a", p.currentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(1, p.getIntValue());
                // and second entry must NOT be lost:
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals("b", p.currentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(2, p.getIntValue());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Same, but name of second entry is the one not matched
    @Test
    public void testNonMatchingSecondNameDefiniteLength() throws Exception
    {
        final byte[] DOC = twoEntryObject(false);

        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(DOC, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertTrue(p.nextFieldName(new SerializedString("a")));
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(1, p.getIntValue());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertToken(JsonToken.FIELD_NAME, p.currentToken());
                assertEquals("b", p.currentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(2, p.getIntValue());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Matching name, but read from a stream that only has partial content
    // buffered, so that the fast path cannot be used either
    @Test
    public void testMatchingNameDefiniteLengthThrottled() throws Exception
    {
        final byte[] DOC = twoEntryObject(false);

        try (JsonParser p = cborParser(DOC, true)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertTrue(p.nextFieldName(new SerializedString("a")));
            assertEquals(1, p.nextIntValue(-1));
            assertTrue(p.nextFieldName(new SerializedString("b")));
            assertEquals(2, p.nextIntValue(-1));
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    // Single-entry definite-length Object: used to report END_OBJECT (and
    // `null` name) instead of the entry it does contain
    @Test
    public void testNonMatchingNameSingleEntry() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x61); bytes.write('a');
        bytes.write(0x01);
        final byte[] DOC = bytes.toByteArray();

        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(DOC, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertToken(JsonToken.FIELD_NAME, p.currentToken());
                assertEquals("a", p.currentName());
                assertEquals(1, p.nextIntValue(-1));
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Nested definite-length Objects, to verify context handling
    @Test
    public void testNonMatchingNameNested() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA2); // Object, 2 entries
        bytes.write(0x61); bytes.write('a');
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x61); bytes.write('c');
        bytes.write(0x03);
        bytes.write(0x61); bytes.write('b');
        bytes.write(0x02);
        final byte[] DOC = bytes.toByteArray();

        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(DOC, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertEquals("a", p.currentName());
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertEquals("c", p.currentName());
                assertEquals(3, p.nextIntValue(-1));
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals("b", p.currentName());
                assertEquals(2, p.nextIntValue(-1));
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Fast path is also skipped for names that are not (definite-length) Strings;
    // all of these cases must decode the name without losing the second entry.
    // First: name as indefinite-length (chunked) String
    @Test
    public void testChunkedNameDefiniteLength() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA2); // Object, 2 entries
        bytes.write(0x7F); // indefinite-length text...
        bytes.write(0x61); bytes.write('a');
        bytes.write(0x61); bytes.write('b');
        bytes.write(0xFF); // ... up to break
        bytes.write(0x01);
        bytes.write(0x61); bytes.write('b');
        bytes.write(0x02);

        _testNonTextName(bytes.toByteArray(), "ab");
    }

    // Second: name as Integer (CBOR allows non-String Object keys)
    @Test
    public void testIntegerNameDefiniteLength() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA2); // Object, 2 entries
        bytes.write(0x01); // name: 1
        bytes.write(0x01);
        bytes.write(0x61); bytes.write('b');
        bytes.write(0x02);

        _testNonTextName(bytes.toByteArray(), "1");
    }

    // Third: tag-prefixed name (fast path only handles untagged text)
    @Test
    public void testTaggedNameDefiniteLength() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA2); // Object, 2 entries
        // Tag 55799 ("Self-Described CBOR"): semantics do not constrain the
        // tagged value, unlike f.ex tag 0 (date/time String)
        bytes.write(0xD9); bytes.write(0xD9); bytes.write(0xF7);
        bytes.write(0x62); bytes.write('a'); bytes.write('b');
        bytes.write(0x01);
        bytes.write(0x61); bytes.write('b');
        bytes.write(0x02);

        _testNonTextName(bytes.toByteArray(), "ab");
    }

    private void _testNonTextName(byte[] doc, String expName) throws Exception
    {
        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(doc, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertToken(JsonToken.FIELD_NAME, p.currentToken());
                assertEquals(expName, p.currentName());
                assertEquals(1, p.nextIntValue(-1));
                // second entry must NOT be lost:
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals("b", p.currentName());
                assertEquals(2, p.nextIntValue(-1));
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Indefinite-length Object (what `CBORGenerator` writes): was, and
    // stays, unaffected
    @Test
    public void testNonMatchingNameIndefiniteLength() throws Exception
    {
        final byte[] DOC = twoEntryObject(true);

        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(DOC, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertToken(JsonToken.FIELD_NAME, p.currentToken());
                assertEquals("a", p.currentName());
                assertEquals(1, p.nextIntValue(-1));
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals("b", p.currentName());
                assertEquals(2, p.nextIntValue(-1));
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Also: for indefinite-length Object, name decoding may find the break
    // marker instead of a name, and must then close the Object properly
    @Test
    public void testBreakInPlaceOfNameIndefiniteLength() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xBF); // start indefinite-length Object
        bytes.write(0x61); bytes.write('a');
        bytes.write(0x01);
        bytes.write(0xFF); // end indefinite-length Object
        final byte[] DOC = bytes.toByteArray();

        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(DOC, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertTrue(p.nextFieldName(new SerializedString("a")));
                assertEquals(1, p.nextIntValue(-1));
                // no more entries; break marker where name would be
                assertFalse(p.nextFieldName(new SerializedString("zzz")));
                assertToken(JsonToken.END_OBJECT, p.currentToken());
                assertNull(p.nextToken());
            }
        }
    }

    // {"a":1, "b":2}, either as definite- or indefinite-length Object
    private byte[] twoEntryObject(boolean indefinite) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(indefinite ? 0xBF : 0xA2);
        bytes.write(0x61); bytes.write('a');
        bytes.write(0x01);
        bytes.write(0x61); bytes.write('b');
        bytes.write(0x02);
        if (indefinite) {
            bytes.write(0xFF);
        }
        return bytes.toByteArray();
    }

}
