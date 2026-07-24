package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;

import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#727]: `nextName(SerializableString)` used to confuse
// 5-bit length marker 23 (length as-is) with 24 ("1-byte length suffix follows")
public class NextFieldName727Test extends CBORTestBase
{
    // Name of exactly 23 bytes, starting with control character U+0017 (0x17):
    // the mis-decoded length marker used to consume that first name byte as
    // the length, which made a 23-character name of a different value match
    @Test
    public void testNoFalseMatchForLength23Name() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xBF); // start indefinite-length Object
        bytes.write(0x60 + 23); // text, length 23 as-is
        bytes.write(0x17); // first name byte: U+0017
        for (int i = 0; i < 22; ++i) {
            bytes.write('a');
        }
        bytes.write(0x61); // text, length 1
        bytes.write('v');
        bytes.write(0xFF); // end indefinite-length Object
        final byte[] DOC = bytes.toByteArray();

        final String actualName = "\u0017" + repeat('a', 22);
        final SerializableString differentName = new SerializedString(repeat('a', 23));

        for (boolean stream : new boolean[] { false, true }) {
            try (JsonParser p = _parser(DOC, stream)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertFalse(p.nextName(differentName));
                assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
                assertEquals(actualName, p.currentName());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("v", p.getString());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }

            // ... and the actual name of course still matches
            try (JsonParser p = _parser(DOC, stream)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertTrue(p.nextName(new SerializedString(actualName)));
                assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
                assertEquals(actualName, p.currentName());
                assertEquals("v", p.nextStringValue());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    // Matching and non-matching names across all length encodings the
    // fast path may see (inline length, 1-byte suffix, 2-byte suffix)
    @Test
    public void testNameLengths() throws Exception
    {
        for (int len : new int[] { 1, 2, 3, 22, 23, 24, 25, 100, 255, 256, 1000 }) {
            final String name = repeat('a', len - 1) + 'b';
            final String differentName = repeat('a', len - 1) + 'c';

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (JsonGenerator g = cborGenerator(bytes)) {
                g.writeStartObject();
                g.writeStringProperty(name, "v");
                g.writeEndObject();
            }
            final byte[] doc = bytes.toByteArray();

            for (boolean stream : new boolean[] { false, true }) {
                try (JsonParser p = _parser(doc, stream)) {
                    assertToken(JsonToken.START_OBJECT, p.nextToken());
                    assertTrue(p.nextName(new SerializedString(name)),
                            "Should match name of length "+len+" (stream? "+stream+")");
                    assertEquals(name, p.currentName());
                    assertEquals("v", p.nextStringValue());
                    assertToken(JsonToken.END_OBJECT, p.nextToken());
                    assertNull(p.nextToken());
                }
                try (JsonParser p = _parser(doc, stream)) {
                    assertToken(JsonToken.START_OBJECT, p.nextToken());
                    assertFalse(p.nextName(new SerializedString(differentName)),
                            "Should not match different name of length "+len+" (stream? "+stream+")");
                    assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
                    assertEquals(name, p.currentName());
                    assertEquals("v", p.nextStringValue());
                    assertToken(JsonToken.END_OBJECT, p.nextToken());
                    assertNull(p.nextToken());
                }
            }
        }
    }

    // Non-canonical encoding: short name written with 1-byte length suffix
    @Test
    public void testNonCanonicalLengthEncoding() throws Exception
    {
        final String name = "abcde";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xBF); // start indefinite-length Object
        bytes.write(0x60 + 24); // text, 1-byte length suffix...
        bytes.write(name.length()); // ... for length that would fit in marker
        for (int i = 0; i < name.length(); ++i) {
            bytes.write(name.charAt(i));
        }
        bytes.write(0x61);
        bytes.write('v');
        bytes.write(0xFF); // end indefinite-length Object
        final byte[] DOC = bytes.toByteArray();

        try (JsonParser p = _parser(DOC, false)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertTrue(p.nextName(new SerializedString(name)));
            assertEquals(name, p.currentName());
            assertEquals("v", p.nextStringValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }

        try (JsonParser p = _parser(DOC, false)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertFalse(p.nextName(new SerializedString("abcdX")));
            assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
            assertEquals(name, p.currentName());
        }
    }

    private JsonParser _parser(byte[] doc, boolean stream) throws Exception {
        if (!stream) {
            return cborParser(doc);
        }
        // read one byte at a time, to force the "not enough buffered" fallback
        return cborParser(new ThrottledInputStream(new ByteArrayInputStream(doc), 1));
    }

    private String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }
}
