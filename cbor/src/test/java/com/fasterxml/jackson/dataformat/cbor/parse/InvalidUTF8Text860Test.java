package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#860]: invalid UTF-8 sequences (overlong, surrogates,
// beyond U+10FFFF, bad continuation bytes) must be rejected
public class InvalidUTF8Text860Test extends CBORTestBase
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
        { 0xF0, 0x41, 0x41, 0x41 }, // bad continuation bytes
        { 0xF0, 0x9F, 0x98, 0x41 }, // bad last continuation byte
    };

    private final static String[] EXPECTED_MSG = {
        // lead bytes that are never valid: rejected as such (wording differs
        // between decoding paths for names, but all include "byte 0x..")
        "byte 0xc0",
        "byte 0xc1",
        "overlong 3-byte encoding of 0x2f",
        "overlong 3-byte encoding of 0x7ff",
        "overlong 4-byte encoding of 0x2f",
        "overlong 4-byte encoding of 0xffff",
        "Illegal surrogate character 0xd800",
        "Illegal surrogate character 0xdfff",
        "code point 0x110000 beyond U+10FFFF",
        "byte 0xf5",
        "Invalid UTF-8 middle byte 0x41",
        "Invalid UTF-8 middle byte 0x41",
    };

    private final static String[] VALID = {
        "\u0080", "\u07FF", "\u0800", "\uD7FF", "\uE000", "\uFFFF",
        "\uD800\uDC00", // U+10000
        "\uDBFF\uDFFF", // U+10FFFF
    };

    @Test
    public void testInvalidStringValue() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                byte[] doc = textDoc(payload(pad, INVALID[i]), false);
                _verifyFails(cborParser(doc), false, EXPECTED_MSG[i], pad);
                _verifyFails(cborParser(new ByteArrayInputStream(doc)), false, EXPECTED_MSG[i], pad);
            }
        }
    }

    @Test
    public void testInvalidChunkedStringValue() throws Exception
    {
        for (int pad : PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                byte[] doc = textDoc(payload(pad, INVALID[i]), true);
                _verifyFails(cborParser(doc), false, EXPECTED_MSG[i], pad);
                _verifyFails(cborParser(new ByteArrayInputStream(doc)), false, EXPECTED_MSG[i], pad);
            }
        }
    }

    @Test
    public void testInvalidPropertyName() throws Exception
    {
        // short names, longer names, and names longer than input buffer
        // (but within default max name length)
        for (int pad : new int[] { 1, 100, 10000 }) {
            for (int i = 0; i < INVALID.length; ++i) {
                byte[] doc = nameDoc(payload(pad, INVALID[i]), false);
                _verifyFails(cborParser(doc), true, EXPECTED_MSG[i], pad);
                _verifyFails(cborParser(new ByteArrayInputStream(doc)), true, EXPECTED_MSG[i], pad);
                doc = nameDoc(payload(pad, INVALID[i]), true);
                _verifyFails(cborParser(doc), true, EXPECTED_MSG[i], pad);
                _verifyFails(cborParser(new ByteArrayInputStream(doc)), true, EXPECTED_MSG[i], pad);
            }
        }
    }

    // Parser must remain usable after invalid String value: error location
    // points to the problem, but parsing continues after the String
    @Test
    public void testRecoverAfterInvalidStringValue() throws Exception
    {
        // only paddings where String is decoded from within input buffer
        for (int pad : new int[] { 1, 100 }) {
            for (int i = 0; i < INVALID.length; ++i) {
                final byte[] text = payload(pad, INVALID[i]);
                final byte[] first = textDoc(text, false);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(first, 0, first.length);
                byte[] second = textDoc("ok".getBytes("UTF-8"), false);
                out.write(second, 0, second.length);
                final byte[] doc = out.toByteArray();
                final int textStart = first.length - text.length;

                for (boolean stream : new boolean[] { false, true }) {
                    try (CBORParser p = stream ? cborParser(new ByteArrayInputStream(doc))
                            : cborParser(doc)) {
                        assertToken(JsonToken.VALUE_STRING, p.nextToken());
                        try {
                            p.getText();
                            fail("Should have failed");
                        } catch (StreamReadException e) {
                            verifyException(e, EXPECTED_MSG[i]);
                            long offset = e.getLocation().getByteOffset();
                            assertTrue(offset > textStart && offset <= first.length,
                                    "Error offset "+offset+" not within String ["
                                    +textStart+", "+first.length+"]");
                        }
                        assertToken(JsonToken.VALUE_STRING, p.nextToken());
                        assertEquals("ok", p.getText());
                        assertNull(p.nextToken());
                    }
                }
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
                for (boolean chunked : new boolean[] { false, true }) {
                    byte[] doc = textDoc(payload(pad, utf8), chunked);
                    try (CBORParser p = cborParser(new ByteArrayInputStream(doc))) {
                        assertToken(JsonToken.VALUE_STRING, p.nextToken());
                        assertEquals(exp, p.getText());
                    }
                    if (!chunked) {
                        try (CBORParser p = cborParser(doc)) {
                            assertToken(JsonToken.VALUE_STRING, p.nextToken());
                            assertEquals(exp, p.getText());
                        }
                    }
                    if (pad < 1000) {
                        doc = nameDoc(payload(pad, utf8), chunked);
                        try (CBORParser p = cborParser(new ByteArrayInputStream(doc))) {
                            assertToken(JsonToken.START_OBJECT, p.nextToken());
                            assertToken(JsonToken.FIELD_NAME, p.nextToken());
                            assertEquals(exp, p.currentName());
                        }
                    }
                }
            }
        }
    }

    private void _verifyFails(CBORParser p, boolean name, String expMsg, int pad) throws Exception
    {
        try (CBORParser parser = p) {
            String str;
            if (name) {
                assertToken(JsonToken.START_OBJECT, parser.nextToken());
                assertToken(JsonToken.FIELD_NAME, parser.nextToken());
                str = parser.currentName();
            } else {
                assertToken(JsonToken.VALUE_STRING, parser.nextToken());
                str = parser.getText();
            }
            fail("Should have failed (padding "+pad+"), did not, got: "+str.substring(pad));
        } catch (StreamReadException e) {
            // invalid bytes reported as-is; decoded code point problems note property name
            if (name && !expMsg.contains("byte 0x")) {
                expMsg = "Invalid UTF-8 in Object property name: "+expMsg;
            }
            verifyException(e, expMsg);
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

    private static byte[] textDoc(byte[] text, boolean chunked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _writeText(out, text, chunked);
        return out.toByteArray();
    }

    private static byte[] nameDoc(byte[] text, boolean chunked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA1); // map with 1 entry
        _writeText(out, text, chunked);
        out.write(0x01);
        return out.toByteArray();
    }

    private static void _writeText(ByteArrayOutputStream out, byte[] text, boolean chunked) {
        if (chunked) {
            out.write(0x7F);
        }
        final int len = text.length;
        if (len < 24) {
            out.write(0x60 | len);
        } else if (len < 256) {
            out.write(0x78);
            out.write(len);
        } else if (len < 65536) {
            out.write(0x79);
            out.write(len >> 8);
            out.write(len);
        } else {
            out.write(0x7A);
            out.write(len >>> 24);
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        }
        out.write(text, 0, len);
        if (chunked) {
            out.write(0xFF);
        }
    }
}
