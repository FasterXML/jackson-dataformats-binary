package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

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
        { 0xE2, 0x41, 0x82 }, // bad first continuation byte
        { 0xE2, 0xC3, 0x82 }, // lead byte instead of continuation byte
        { 0xF0, 0xF0, 0x98, 0x80 }, // lead byte instead of continuation byte
    };

    private final static String[] EXPECTED_MSG = {
        // 0xC0/0xC1 only produce overlong forms: invalid as lead bytes
        "unexpected byte 0xc0",
        "unexpected byte 0xc1",
        "overlong 3-byte encoding of 0x2f",
        "overlong 3-byte encoding of 0x7ff",
        "overlong 4-byte encoding of 0x2f",
        "overlong 4-byte encoding of 0xffff",
        "Illegal surrogate character 0xd800",
        "Illegal surrogate character 0xdfff",
        "code point 0x110000 beyond U+10FFFF",
        // 0xF5 - 0xF7 only produce > U+10FFFF: invalid as lead bytes
        "unexpected byte 0xf5",
        "bad continuation byte 0x41",
        "bad continuation byte 0x41",
        "bad continuation byte 0x41",
        "bad continuation byte 0x41",
        "bad continuation byte 0xc3",
        "bad continuation byte 0xf0",
    };

    private final static String[] VALID = {
        "\u0080", "\u07FF", "\u0800", "\uD7FF", "\uE000", "\uFFFF",
        "\uD800\uDC00", // U+10000
        "\uDBFF\uDFFF", // U+10FFFF
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

    // Stray continuation byte, or byte never valid in UTF-8: same error for
    // short and long (past input buffer) text
    @Test
    public void testInvalidLeadByte() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int b : new int[] { 0x80, 0xBF, 0xF8, 0xFF }) {
                byte[] doc = valueDoc(payload(pad, new int[] { b }));
                _verifyFails(doc, false,
                        "unexpected byte 0x"+Integer.toHexString(b), pad);
            }
        }
    }

    // Multi-byte sequence truncated by end of text: message should
    // indicate full length of sequence, for short and long text
    @Test
    public void testTruncatedAtEnd() throws Exception
    {
        final int[][] leads = { { 0xC3 }, { 0xE2, 0x82 }, { 0xF0, 0x9F, 0x98 } };
        for (int pad : PADDINGS) {
            for (int[] lead : leads) {
                byte[] text = payload(pad, lead);
                // drop the trailing 'b' so sequence is truncated by end of text
                byte[] doc = valueDoc(Arrays.copyOf(text, text.length - 1));
                final int expLen = lead.length + 1;
                _verifyFails(doc, false, "truncated "+expLen+"-byte sequence", pad);
            }
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
                // map keys should be identified as such; values not
                if (mapKey != e.getMessage().contains("Object property name")) {
                    fail("Map key "+(mapKey ? "not " : "")+"indicated in message: "+e.getMessage());
                }
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
