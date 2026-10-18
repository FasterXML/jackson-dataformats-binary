package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#861]: invalid UTF-8 sequences (overlong, surrogates,
// beyond U+10FFFF, bad continuation bytes) must be rejected
public class InvalidUTF8Text861Test extends BaseTestForSmile
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    // Padding before the sequence: tiny, short, long and past-buffer text
    private final static int[] VALUE_PADDINGS = { 1, 40, 100, 70000 };

    // Padding before the sequence: short and long names
    private final static int[] NAME_PADDINGS = { 1, 100 };

    // Bytes fed per call to non-blocking parser
    private final static int[] ASYNC_CHUNKS = { 1, 3, 100000 };

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

    private final SmileFactory F = new SmileFactory();

    @Test
    public void testInvalidStringValue() throws Exception
    {
        for (int pad : VALUE_PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                _verifyFailsAll(valueDoc(payload(pad, INVALID[i])), false, EXPECTED_MSG[i], pad);
            }
        }
    }

    @Test
    public void testInvalidPropertyName() throws Exception
    {
        for (int pad : NAME_PADDINGS) {
            for (int i = 0; i < INVALID.length; ++i) {
                _verifyFailsAll(nameDoc(payload(pad, INVALID[i])), true, EXPECTED_MSG[i], pad);
            }
        }
    }

    @Test
    public void testValidBoundaryChars() throws Exception
    {
        for (String str : VALID) {
            final byte[] utf8 = str.getBytes("UTF-8");
            for (int pad : VALUE_PADDINGS) {
                final String exp = pad(pad) + str + "b";
                final byte[] doc = valueDoc(payload(pad, utf8));
                assertEquals(exp, _readBlocking(_smileParser(doc), false));
                assertEquals(exp, _readBlocking(_smileParser(new ByteArrayInputStream(doc)), false));
                for (int chunk : ASYNC_CHUNKS) {
                    assertEquals(exp, _readAsync(doc, chunk, false));
                }
            }
            for (int pad : NAME_PADDINGS) {
                final String exp = pad(pad) + str + "b";
                final byte[] doc = nameDoc(payload(pad, utf8));
                assertEquals(exp, _readBlocking(_smileParser(doc), true));
                assertEquals(exp, _readBlocking(_smileParser(new ByteArrayInputStream(doc)), true));
                for (int chunk : ASYNC_CHUNKS) {
                    assertEquals(exp, _readAsync(doc, chunk, true));
                }
            }
        }
    }

    private void _verifyFailsAll(byte[] doc, boolean name, String expMsg, int pad) throws Exception
    {
        _verifyFails(doc, name, expMsg, pad, 0);
        _verifyFails(doc, name, expMsg, pad, -1);
        for (int chunk : ASYNC_CHUNKS) {
            _verifyFails(doc, name, expMsg, pad, chunk);
        }
    }

    // `mode`: 0 for byte[], -1 for InputStream, positive for async chunk size
    private void _verifyFails(byte[] doc, boolean name, String expMsg, int pad, int mode) throws Exception
    {
        try {
            String str;
            if (mode == 0) {
                str = _readBlocking(_smileParser(doc), name);
            } else if (mode < 0) {
                str = _readBlocking(_smileParser(new ByteArrayInputStream(doc)), name);
            } else {
                str = _readAsync(doc, mode, name);
            }
            fail("Should have failed (padding "+pad+", mode "+mode+"), did not, got: "
                    +str.substring(pad));
        } catch (StreamReadException e) {
            verifyException(e, expMsg);
        }
    }

    private String _readBlocking(JsonParser p, boolean name) throws Exception
    {
        try (JsonParser parser = p) {
            if (name) {
                assertToken(JsonToken.START_OBJECT, parser.nextToken());
                assertToken(JsonToken.FIELD_NAME, parser.nextToken());
                return parser.currentName();
            }
            assertToken(JsonToken.VALUE_STRING, parser.nextToken());
            return parser.getText();
        }
    }

    private String _readAsync(byte[] doc, int chunk, boolean name) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            final ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            int offset = 0;
            while (true) {
                JsonToken t = p.nextToken();
                if (t == JsonToken.NOT_AVAILABLE) {
                    if (offset < doc.length) {
                        int end = Math.min(doc.length, offset + chunk);
                        feeder.feedInput(doc, offset, end);
                        offset = end;
                    } else {
                        feeder.endOfInput();
                    }
                    continue;
                }
                if (t == null) {
                    fail("Unexpected end of content");
                }
                if (name && t == JsonToken.FIELD_NAME) {
                    return p.currentName();
                }
                if (!name && t == JsonToken.VALUE_STRING) {
                    return p.getText();
                }
            }
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

    private static byte[] valueDoc(byte[] text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER, 0, HEADER.length);
        final int len = text.length;
        if (len <= 33) { // tiny Unicode
            out.write(SmileConstants.TOKEN_PREFIX_TINY_UNICODE + len - 2);
            out.write(text, 0, len);
        } else if (len <= 65) { // short Unicode
            out.write(SmileConstants.TOKEN_PREFIX_SHORT_UNICODE + len - 34);
            out.write(text, 0, len);
        } else {
            out.write(SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE);
            out.write(text, 0, len);
            out.write(SmileConstants.BYTE_MARKER_END_OF_STRING);
        }
        return out.toByteArray();
    }

    private static byte[] nameDoc(byte[] text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER, 0, HEADER.length);
        out.write(SmileConstants.TOKEN_LITERAL_START_OBJECT);
        final int len = text.length;
        if (len <= 57) { // short Unicode name
            out.write(SmileConstants.TOKEN_PREFIX_KEY_UNICODE + len - 2);
            out.write(text, 0, len);
        } else {
            out.write(SmileConstants.TOKEN_KEY_LONG_STRING);
            out.write(text, 0, len);
            out.write(SmileConstants.BYTE_MARKER_END_OF_STRING);
        }
        out.write(0xC2); // small int 1
        out.write(SmileConstants.TOKEN_LITERAL_END_OBJECT);
        return out.toByteArray();
    }
}
