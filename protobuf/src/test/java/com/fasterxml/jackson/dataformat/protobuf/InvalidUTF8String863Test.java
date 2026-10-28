package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#863]: invalid UTF-8 sequences (overlong, surrogates,
// beyond U+10FFFF, bad continuation bytes) must be rejected
public class InvalidUTF8String863Test extends ProtobufTestBase
{
    // Padding before the sequence: short text, longer text, past input buffer
    private final static int[] PADDINGS = { 1, 100, 70000 };

    private final static int[][] INVALID = {
        { 0xC0, 0xAF }, // overlong '/'
        { 0xC1, 0xBF }, // overlong 0x7F
        { 0xE0, 0x80, 0xAF }, // overlong '/'
        { 0xE0, 0x9F, 0xBF }, // overlong 0x7FF
        { 0xF0, 0x80, 0x80, 0xAF }, // overlong '/'
        { 0xF0, 0x8F, 0xBF, 0xBF }, // overlong 0xFFFF
        { 0xED, 0xA0, 0x80 }, // surrogate 0xD800
        { 0xED, 0xBF, 0xBF }, // surrogate 0xDFFF
        { 0xF4, 0x90, 0x80, 0x80 }, // 0x110000
        { 0xF5, 0x80, 0x80, 0x80 }, // 0x140000
        { 0xC3, 0x41 }, // bad continuation byte
        { 0xE2, 0x82, 0x41 }, // bad last continuation byte
        { 0xF0, 0x9F, 0x98, 0x41 }, // bad last continuation byte
        { 0xE2, 0x41, 0x82 }, // bad first continuation byte
        { 0xE2, 0xC3, 0x82 }, // lead byte instead of continuation byte
        { 0xF0, 0xF0, 0x98, 0x80 }, // lead byte instead of continuation byte
    };

    // Offset (from start of sequence) at which problem is detected:
    // for continuation bytes, right after bad byte; otherwise after last byte
    private final static int[] EXPECTED_OFFSET = {
        2, 2, 3, 3, 4, 4, 3, 3, 4, 4, 2, 3, 4, 2, 2, 2
    };

    private final static String[] EXPECTED_MSG = {
        "overlong 2-byte encoding of 0x2f",
        "overlong 2-byte encoding of 0x7f",
        "overlong 3-byte encoding of 0x2f",
        "overlong 3-byte encoding of 0x7ff",
        "overlong 4-byte encoding of 0x2f",
        "overlong 4-byte encoding of 0xffff",
        "Illegal surrogate character 0xd800",
        "Illegal surrogate character 0xdfff",
        "code point 0x110000 beyond U+10FFFF",
        "code point 0x140000 beyond U+10FFFF",
        "bad continuation byte 0x41",
        "bad continuation byte 0x41",
        "bad continuation byte 0x41",
        "bad continuation byte 0x41",
        "bad continuation byte 0xc3",
        "bad continuation byte 0xf0",
    };

    private final static String[] VALID = {
        "\u0080", "߿", "ࠀ", "퟿", "", "￿",
        "𐀀", // U+10000
        "􏿿", // U+10FFFF
    };

    private final ProtobufMapper MAPPER = newObjectMapper();

    private final ProtobufSchema SCHEMA;
    private final ProtobufSchema MAP_SCHEMA;
    {
        try {
            SCHEMA = ProtobufSchemaLoader.std.parse(
                    "message M { optional string s = 1; optional int32 i = 2; }");
            MAP_SCHEMA = ProtobufSchemaLoader.std.parse(
                    "syntax = \"proto3\";\nmessage M { map<string, int32> m = 1; }\n", "M");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInvalidString() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                _verifyFails(payload(pad, INVALID[i]), EXPECTED_MSG[i], pad,
                        pad + EXPECTED_OFFSET[i]);
            }
        }
    }

    // Multi-byte character truncated by String length must not be decoded
    // using bytes of the following field
    @Test
    public void testTruncatedString() throws Exception
    {
        for (int pad : PADDINGS) {
            // problem detected at the lead byte
            _verifyFails(text(pad, new int[] { 0xC3 }),
                    "truncated 2-byte sequence at end of text", pad, pad + 1);
            _verifyFails(text(pad, new int[] { 0xE2, 0x82 }),
                    "truncated 3-byte sequence at end of text", pad, pad + 1);
            _verifyFails(text(pad, new int[] { 0xF0, 0x9F, 0x98 }),
                    "truncated 4-byte sequence at end of text", pad, pad + 1);
        }
    }

    @Test
    public void testInvalidLeadByte() throws Exception
    {
        for (int pad : PADDINGS) {
            _verifyFails(payload(pad, new int[] { 0x80 }),
                    "unexpected byte 0x80", pad, pad + 1);
            _verifyFails(payload(pad, new int[] { 0xF8 }),
                    "unexpected byte 0xf8", pad, pad + 1);
        }
    }

    @Test
    public void testValidBoundaryChars() throws Exception
    {
        for (int pad : PADDINGS) {
            for (String str : VALID) {
                String exp = pad(pad) + str + "b";
                byte[] doc = doc(payload(pad, str.getBytes("UTF-8")));
                assertEquals(exp, _read(MAPPER.createParser(doc)));
                assertEquals(exp, _read(MAPPER.createParser(new ByteArrayInputStream(doc))));
            }
        }
    }

    // `string` map keys are decoded via separate code path; problem should be
    // reported as being in property name
    @Test
    public void testInvalidMapKey() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                final byte[] doc = mapDoc(payload(pad, INVALID[i]));
                for (int mode = 0; mode < 2; ++mode) {
                    try (JsonParser p = (mode == 0) ? MAPPER.createParser(doc)
                            : MAPPER.createParser(new ByteArrayInputStream(doc))) {
                        p.setSchema(MAP_SCHEMA);
                        assertToken(JsonToken.START_OBJECT, p.nextToken());
                        assertToken(JsonToken.FIELD_NAME, p.nextToken());
                        assertToken(JsonToken.START_OBJECT, p.nextToken());
                        p.nextToken();
                        fail("Should have failed (padding "+pad+", mode "+mode+"), did not, got: "
                                +p.currentName().substring(pad));
                    } catch (StreamReadException e) {
                        verifyException(e, "Invalid UTF-8 in Object property name: "
                                +EXPECTED_MSG[i]);
                    }
                }
            }
        }
    }

    // Failure to decode String should leave parser after String, so that
    // following field can still be read
    @Test
    public void testContinueAfterInvalidString() throws Exception
    {
        // (only for String values within input buffer: longer ones are decoded
        // while reading)
        for (int pad : new int[] { 1, 100 }) {
            for (int i = 0; i < INVALID.length; ++i) {
                final byte[] doc = doc(payload(pad, INVALID[i]));
                for (int mode = 0; mode < 2; ++mode) {
                    try (JsonParser p = (mode == 0) ? MAPPER.createParser(doc)
                            : MAPPER.createParser(new ByteArrayInputStream(doc))) {
                        p.setSchema(SCHEMA);
                        assertToken(JsonToken.START_OBJECT, p.nextToken());
                        assertToken(JsonToken.FIELD_NAME, p.nextToken());
                        assertToken(JsonToken.VALUE_STRING, p.nextToken());
                        try {
                            p.getText();
                            fail("Should have failed");
                        } catch (StreamReadException e) {
                            verifyException(e, EXPECTED_MSG[i]);
                        }
                        assertToken(JsonToken.FIELD_NAME, p.nextToken());
                        assertEquals("i", p.currentName());
                        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                        assertEquals(1, p.getIntValue());
                        assertToken(JsonToken.END_OBJECT, p.nextToken());
                    }
                }
            }
        }
    }

    // 4-byte character right after output segment has been filled: must not
    // write past end of segment (long text, decoded while reading)
    @Test
    public void testLongTextWithSurrogatePairAtSegmentEnd() throws Exception
    {
        final String emoji = "\uD83D\uDE00"; // U+1F600
        final int len = 100_000;
        for (int prefix = 65530; prefix <= 65540; ++prefix) {
            StringBuilder sb = new StringBuilder(pad(prefix)).append(emoji);
            sb.append(pad(len - sb.length()));
            final String exp = sb.toString();
            byte[] doc = doc(exp.getBytes("UTF-8"));
            try (JsonParser p = MAPPER.createParser(new ByteArrayInputStream(doc))) {
                p.setSchema(SCHEMA);
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(exp, p.getText(), "Prefix length "+prefix);
            }
        }
    }

    // `expOffset`: expected error offset relative to start of String contents
    private void _verifyFails(byte[] text, String expMsg, int pad, int expOffset)
        throws Exception
    {
        final byte[] doc = doc(text);
        // String contents are followed by 2-byte int field
        final int textStart = doc.length - 2 - text.length;
        for (int mode = 0; mode < 2; ++mode) {
            JsonParser p = (mode == 0) ? MAPPER.createParser(doc)
                    : MAPPER.createParser(new ByteArrayInputStream(doc));
            try {
                String str = _read(p);
                fail("Should have failed (padding "+pad+", mode "+mode+"), did not, got: "
                        +str.substring(pad));
            } catch (StreamReadException e) {
                verifyException(e, expMsg);
                assertEquals(textStart + expOffset, e.getLocation().getByteOffset(),
                        "Error offset (padding "+pad+", mode "+mode+")");
            }
        }
    }

    private String _read(JsonParser p) throws Exception
    {
        try (JsonParser parser = p) {
            parser.setSchema(SCHEMA);
            assertToken(JsonToken.START_OBJECT, parser.nextToken());
            assertToken(JsonToken.FIELD_NAME, parser.nextToken());
            assertToken(JsonToken.VALUE_STRING, parser.nextToken());
            String str = parser.getText();
            // and following int field should be intact
            assertToken(JsonToken.FIELD_NAME, parser.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(1, parser.getIntValue());
            return str;
        }
    }

    private static String pad(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append('a');
        }
        return sb.toString();
    }

    private static byte[] payload(int pad, int[] seq) {
        return payload(pad, bytes(seq));
    }

    // Like `payload()` but with `seq` at the very end of String
    private static byte[] text(int pad, int[] seq) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < pad; ++i) {
            out.write('a');
        }
        byte[] b = bytes(seq);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    private static byte[] bytes(int[] seq) {
        byte[] b = new byte[seq.length];
        for (int i = 0; i < seq.length; ++i) {
            b[i] = (byte) seq[i];
        }
        return b;
    }

    private static byte[] payload(int pad, byte[] seq) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < pad; ++i) {
            out.write('a');
        }
        out.write(seq, 0, seq.length);
        out.write('b');
        return out.toByteArray();
    }

    // String field 1, followed by int32 field 2 with value 1
    private static byte[] doc(byte[] text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A); // field 1, wire type 2 (length-delimited)
        int len = text.length;
        while ((len & ~0x7F) != 0) {
            out.write((len & 0x7F) | 0x80);
            len >>>= 7;
        }
        out.write(len);
        out.write(text, 0, text.length);
        out.write(0x10); // field 2, wire type 0 (varint)
        out.write(0x01);
        return out.toByteArray();
    }

    // Map field 1 with single entry: `text` as key, 1 as value
    private static byte[] mapDoc(byte[] text) {
        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        entry.write(0x0A); // key: field 1, wire type 2
        writeLength(entry, text.length);
        entry.write(text, 0, text.length);
        entry.write(0x10); // value: field 2, wire type 0
        entry.write(0x01);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A); // map field 1, wire type 2
        writeLength(out, entry.size());
        byte[] b = entry.toByteArray();
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int len) {
        while ((len & ~0x7F) != 0) {
            out.write((len & 0x7F) | 0x80);
            len >>>= 7;
        }
        out.write(len);
    }
}
