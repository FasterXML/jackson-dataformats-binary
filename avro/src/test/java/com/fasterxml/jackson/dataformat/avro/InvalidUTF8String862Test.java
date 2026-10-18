package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#862]: invalid UTF-8 sequences (overlong, surrogates,
// beyond U+10FFFF, bad continuation bytes) must be rejected
public class InvalidUTF8String862Test extends AvroTestBase
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

    private final AvroMapper MAPPER = newMapper();

    private final AvroSchema STRING_SCHEMA = parseSchema(MAPPER, aposToQuotes(
            "{'type':'record','name':'R','fields':[{'name':'s','type':'string'}]}"));

    private final AvroSchema MAP_SCHEMA = parseSchema(MAPPER, aposToQuotes(
            "{'type':'map','values':'int'}"));

    @Test
    public void testInvalidStringValue() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                byte[] doc = valueDoc(payload(pad, INVALID[i]));
                _verifyFails(doc, false, EXPECTED_MSG[i], pad);
            }
        }
    }

    @Test
    public void testInvalidMapKey() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                byte[] doc = mapDoc(payload(pad, INVALID[i]));
                _verifyFails(doc, true, EXPECTED_MSG[i], pad);
            }
        }
    }

    @Test
    public void testInvalidLeadByte() throws Exception
    {
        byte[] doc = valueDoc(payload(1, new int[] { 0x80 }));
        try {
            _read(MAPPER.createParser(doc), false);
            fail("Should have failed");
        } catch (StreamReadException e) {
            verifyException(e, "Invalid byte 0x80 in Unicode text block");
        }
    }

    @Test
    public void testValidBoundaryChars() throws Exception
    {
        for (int pad : PADDINGS) {
            for (String str : VALID) {
                byte[] utf8 = str.getBytes("UTF-8");
                String exp = pad(pad) + str + "b";
                byte[] doc = valueDoc(payload(pad, utf8));
                assertEquals(exp, _read(MAPPER.createParser(doc), false));
                assertEquals(exp, _read(MAPPER.createParser(new ByteArrayInputStream(doc)), false));
                doc = mapDoc(payload(pad, utf8));
                assertEquals(exp, _read(MAPPER.createParser(doc), true));
                assertEquals(exp, _read(MAPPER.createParser(new ByteArrayInputStream(doc)), true));
            }
        }
    }

    private void _verifyFails(byte[] doc, boolean mapKey, String expMsg, int pad) throws Exception
    {
        for (int mode = 0; mode < 2; ++mode) {
            JsonParser p = (mode == 0) ? MAPPER.createParser(doc)
                    : MAPPER.createParser(new ByteArrayInputStream(doc));
            try {
                String str = _read(p, mapKey);
                fail("Should have failed (padding "+pad+", mode "+mode+"), did not, got: "
                        +str.substring(pad));
            } catch (StreamReadException e) {
                verifyException(e, expMsg);
            }
        }
    }

    private String _read(JsonParser p, boolean mapKey) throws Exception
    {
        try (JsonParser parser = p) {
            parser.setSchema(mapKey ? MAP_SCHEMA : STRING_SCHEMA);
            assertToken(JsonToken.START_OBJECT, parser.nextToken());
            assertToken(JsonToken.FIELD_NAME, parser.nextToken());
            if (mapKey) {
                return parser.currentName();
            }
            assertToken(JsonToken.VALUE_STRING, parser.nextToken());
            return parser.getText();
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

    // Record with single `string` field
    private static byte[] valueDoc(byte[] text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _writeLong(out, text.length);
        out.write(text, 0, text.length);
        return out.toByteArray();
    }

    // Map with single entry: block count 1, key, int value 1, end block
    private static byte[] mapDoc(byte[] key) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _writeLong(out, 1);
        _writeLong(out, key.length);
        out.write(key, 0, key.length);
        _writeLong(out, 1);
        _writeLong(out, 0);
        return out.toByteArray();
    }

    // zig-zag variable-length encoding
    private static void _writeLong(ByteArrayOutputStream out, long v) {
        v = (v << 1) ^ (v >> 63);
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }
}
