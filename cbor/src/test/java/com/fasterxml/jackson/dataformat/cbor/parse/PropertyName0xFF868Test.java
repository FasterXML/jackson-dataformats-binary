package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#868]: name with 0xFF byte at start of last quad must not
// match a shorter name already in the symbol table
public class PropertyName0xFF868Test extends CBORTestBase
{
    private final static int MAX_NAME_LEN = 25;

    @Test
    public void testSameDocument() throws Exception
    {
        for (int len = 1; len <= MAX_NAME_LEN; ++len) {
            final byte[] bad = _nameWith0xFF(len);
            if (bad == null) {
                continue;
            }
            final byte[] doc = _mapDoc(_name(len), bad);
            _verifyFails(cborParser(doc), len);
            _verifyFails(cborParser(new ByteArrayInputStream(doc)), len);
        }
    }

    @Test
    public void testNameFromEarlierDocument() throws Exception
    {
        for (int len = 1; len <= MAX_NAME_LEN; ++len) {
            final byte[] bad = _nameWith0xFF(len);
            if (bad == null) {
                continue;
            }
            final CBORFactory f = cborFactory();
            try (CBORParser p = cborParser(f, _mapDoc(_name(len)))) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals(new String(_name(len), "UTF-8"), p.currentName());
            }
            _verifyFails(cborParser(f, _mapDoc(bad)), len);
        }
    }

    private void _verifyFails(CBORParser p, int len) throws Exception
    {
        final StringBuilder names = new StringBuilder();
        try (CBORParser parser = p) {
            JsonToken t;
            while ((t = parser.nextToken()) != null) {
                if (t == JsonToken.FIELD_NAME) {
                    names.append(" '").append(parser.currentName()).append("'");
                }
            }
            fail("Should have failed for name length "+(len+1)+", got names:"+names);
        } catch (StreamReadException e) {
            verifyException(e, "Invalid UTF-8 start byte 0xff");
        }
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
    // its last quad, so that its quads equal those of `_name(len)` once padded;
    // or null if lengths differ in quad count
    private static byte[] _nameWith0xFF(int len) {
        if ((len & 3) == 0) {
            return null;
        }
        final byte[] name = _name(len);
        final int lastQuadStart = (len >> 2) << 2;
        byte[] b = new byte[len+1];
        System.arraycopy(name, 0, b, 0, lastQuadStart);
        b[lastQuadStart] = (byte) 0xFF;
        System.arraycopy(name, lastQuadStart, b, lastQuadStart+1, len - lastQuadStart);
        return b;
    }

    private static byte[] _mapDoc(byte[]... names) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA0 | names.length);
        int value = 1;
        for (byte[] name : names) {
            if (name.length < 24) {
                out.write(0x60 | name.length);
            } else {
                out.write(0x78);
                out.write(name.length);
            }
            out.write(name, 0, name.length);
            out.write(value++);
        }
        return out.toByteArray();
    }
}
