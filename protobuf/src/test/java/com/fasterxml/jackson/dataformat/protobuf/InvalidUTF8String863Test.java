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
        "Invalid UTF-8 middle byte 0x41",
        "Invalid UTF-8 middle byte 0x41",
        "Invalid UTF-8 middle byte 0x41",
    };

    private final static String[] VALID = {
        "\u0080", "߿", "ࠀ", "퟿", "", "￿",
        "𐀀", // U+10000
        "􏿿", // U+10FFFF
    };

    private final ProtobufMapper MAPPER = newObjectMapper();

    private final ProtobufSchema SCHEMA;
    {
        try {
            SCHEMA = ProtobufSchemaLoader.std.parse(
                    "message M { optional string s = 1; optional int32 i = 2; }");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInvalidString() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                _verifyFails(doc(payload(pad, INVALID[i])), EXPECTED_MSG[i], pad);
            }
        }
    }

    // Multi-byte character truncated by String length must not be decoded
    // using bytes of the following field
    @Test
    public void testTruncatedString() throws Exception
    {
        byte[] str = new byte[] { 'a', (byte) 0xC3 };
        _verifyFails(doc(str), "Malformed 2-byte UTF-8 character at the end of Unicode text block", 0);
        str = new byte[] { 'a', (byte) 0xE2, (byte) 0x82 };
        _verifyFails(doc(str), "Malformed 3-byte UTF-8 character at the end of Unicode text block", 0);
    }

    @Test
    public void testInvalidLeadByte() throws Exception
    {
        _verifyFails(doc(payload(1, new int[] { 0x80 })),
                "Invalid byte 0x80 in Unicode text block", 0);
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

    private void _verifyFails(byte[] doc, String expMsg, int pad) throws Exception
    {
        for (int mode = 0; mode < 2; ++mode) {
            JsonParser p = (mode == 0) ? MAPPER.createParser(doc)
                    : MAPPER.createParser(new ByteArrayInputStream(doc));
            try {
                String str = _read(p);
                fail("Should have failed (padding "+pad+", mode "+mode+"), did not, got: "
                        +str.substring(pad));
            } catch (StreamReadException e) {
                verifyException(e, expMsg);
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
        byte[] b = new byte[seq.length];
        for (int i = 0; i < seq.length; ++i) {
            b[i] = (byte) seq[i];
        }
        return payload(pad, b);
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
}
