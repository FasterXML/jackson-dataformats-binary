package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.testutil.ThrottledInputStream;

// [dataformats-binary#728]: `nextFieldName(SerializableString)` used to consume
// an entry of expected-length (definite-length) Object twice, when its
// name-matching fast path did not succeed, truncating the Object
public class NextFieldName728Test extends CBORTestBase
{
    private final CBORFactory F = new CBORFactory();

    // {"a":1, "b":2} as definite-length Object; name not matched
    public void testNonMatchingNameDefiniteLength() throws Exception
    {
        final byte[] DOC = twoEntryObject(false);

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(DOC, stream)) {
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
    public void testNonMatchingSecondNameDefiniteLength() throws Exception
    {
        final byte[] DOC = twoEntryObject(false);

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(DOC, stream)) {
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
    public void testMatchingNameDefiniteLengthThrottled() throws Exception
    {
        final byte[] DOC = twoEntryObject(false);

        try (JsonParser p = _parser(DOC, true)) {
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
    public void testNonMatchingNameSingleEntry() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x61); bytes.write('a');
        bytes.write(0x01);
        final byte[] DOC = bytes.toByteArray();

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(DOC, stream)) {
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

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(DOC, stream)) {
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

    // Indefinite-length Object (what `CBORGenerator` writes): was, and
    // stays, unaffected
    public void testNonMatchingNameIndefiniteLength() throws Exception
    {
        final byte[] DOC = twoEntryObject(true);

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(DOC, stream)) {
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

    private JsonParser _parser(byte[] doc, boolean stream) throws Exception {
        if (!stream) {
            return F.createParser(doc);
        }
        // read one byte at a time, so fast path cannot peek at name
        return F.createParser(new ThrottledInputStream(new ByteArrayInputStream(doc), 1));
    }
}
