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

// [dataformats-binary#869]: name with 0xFF byte at start of last quad must not
// match a shorter name already in the symbol table
public class PropertyName0xFF869Test extends BaseTestForSmile
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    private final static int[] NAME_LENGTHS = {
        1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15, 17, 18, 19, 21, 22, 23,
        // long names
        65, 66, 67, 69, 70, 71
    };

    // Bytes fed per call to non-blocking parser; 0 for blocking from byte[],
    // -1 for blocking from InputStream
    private final static int[] MODES = { 0, -1, 1, 3, 1000 };

    @Test
    public void testSameDocument() throws Exception
    {
        for (int len : NAME_LENGTHS) {
            for (boolean longToken : new boolean[] { false, true }) {
                final byte[] doc = _objectDoc(longToken, _name(len), _nameWith0xFF(len));
                for (int mode : MODES) {
                    _verifyFails(new SmileFactory(), doc, mode, len);
                }
            }
        }
    }

    @Test
    public void testNameFromEarlierDocument() throws Exception
    {
        for (int len : NAME_LENGTHS) {
            for (boolean longToken : new boolean[] { false, true }) {
                for (int mode : MODES) {
                    final SmileFactory f = new SmileFactory();
                    final byte[] doc1 = _objectDoc(longToken, _name(len));
                    assertEquals(new String(_name(len), "UTF-8"), _names(f, doc1, mode));
                    _verifyFails(f, _objectDoc(longToken, _nameWith0xFF(len)), mode, len);
                }
            }
        }
    }

    private void _verifyFails(SmileFactory f, byte[] doc, int mode, int len) throws Exception
    {
        try {
            String names = _names(f, doc, mode);
            fail("Should have failed for name length "+(len+1)+", mode "+mode+"; got names: "+names);
        } catch (StreamReadException e) {
            verifyException(e, "Invalid UTF-8 start byte 0xff");
        }
    }

    // Reads all property names, comma-separated
    private String _names(SmileFactory f, byte[] doc, int mode) throws Exception
    {
        final StringBuilder sb = new StringBuilder();
        if (mode <= 0) {
            try (JsonParser p = (mode == 0) ? f.createParser(doc)
                    : f.createParser(new ByteArrayInputStream(doc))) {
                JsonToken t;
                while ((t = p.nextToken()) != null) {
                    if (t == JsonToken.FIELD_NAME) {
                        _append(sb, p.currentName());
                    }
                }
            }
            return sb.toString();
        }
        try (JsonParser p = f.createNonBlockingByteArrayParser()) {
            final ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            int offset = 0;
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t == JsonToken.NOT_AVAILABLE) {
                    if (offset < doc.length) {
                        int end = Math.min(doc.length, offset + mode);
                        feeder.feedInput(doc, offset, end);
                        offset = end;
                    } else {
                        feeder.endOfInput();
                    }
                } else if (t == JsonToken.FIELD_NAME) {
                    _append(sb, p.currentName());
                }
            }
        }
        return sb.toString();
    }

    private static void _append(StringBuilder sb, String name) {
        if (sb.length() > 0) {
            sb.append(',');
        }
        sb.append(name);
    }

    // ASCII name of given length
    private static byte[] _name(int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; ++i) {
            b[i] = (byte) ('a' + (i % 26));
        }
        return b;
    }

    // Name one byte longer than `_name(len)`, with 0xFF inserted at start of
    // its last quad, so that its quads equal those of `_name(len)` once padded
    // (`len` must not be a multiple of 4)
    private static byte[] _nameWith0xFF(int len) {
        final byte[] name = _name(len);
        final int lastQuadStart = (len >> 2) << 2;
        byte[] b = new byte[len+1];
        System.arraycopy(name, 0, b, 0, lastQuadStart);
        b[lastQuadStart] = (byte) 0xFF;
        System.arraycopy(name, lastQuadStart, b, lastQuadStart+1, len - lastQuadStart);
        return b;
    }

    // Object with given property names (with small int values), using either
    // short (ASCII / Unicode) or long name tokens
    private static byte[] _objectDoc(boolean longToken, byte[]... names) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER, 0, HEADER.length);
        out.write(SmileConstants.TOKEN_LITERAL_START_OBJECT);
        for (byte[] name : names) {
            final int len = name.length;
            if (longToken || len > 56) {
                out.write(SmileConstants.TOKEN_KEY_LONG_STRING);
                out.write(name, 0, len);
                out.write(SmileConstants.BYTE_MARKER_END_OF_STRING);
            } else if (_isAscii(name)) {
                out.write(SmileConstants.TOKEN_PREFIX_KEY_ASCII + len - 1);
                out.write(name, 0, len);
            } else {
                out.write(SmileConstants.TOKEN_PREFIX_KEY_UNICODE + len - 2);
                out.write(name, 0, len);
            }
            out.write(0xC2); // small int 1
        }
        out.write(SmileConstants.TOKEN_LITERAL_END_OBJECT);
        return out.toByteArray();
    }

    private static boolean _isAscii(byte[] b) {
        for (byte x : b) {
            if (x < 0) {
                return false;
            }
        }
        return true;
    }
}
