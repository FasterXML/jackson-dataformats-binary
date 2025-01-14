package tools.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GeneratorShortStringTest extends CBORTestBase
{
    @Test
    public void testEmptyString() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CBORGenerator gen = cborGenerator(out)) {
            // First with String as input
            gen.writeString("");
            gen.close();
        }
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_EMPTY_STRING);

        // then as char[]
        out = new ByteArrayOutputStream();
        try (CBORGenerator gen = cborGenerator(out)) {
            gen.writeString(new char[0], 0, 0);
            gen.close();
        }
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_EMPTY_STRING);
    }

    @Test
    public void testShortTextAsString() throws Exception {
        for (int len = 1; len <= 23; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value);
                gen.close();
                _verifyBytes(out.toByteArray(), (byte) (CBORConstants.PREFIX_TYPE_TEXT + len),
                        value.getBytes("UTF-8"));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    @Test
    public void testShortTextAsCharArray() throws Exception {
        for (int len = 1; len <= 23; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value.toCharArray(), 0, len);
                gen.close();
                _verifyBytes(out.toByteArray(), (byte) (CBORConstants.PREFIX_TYPE_TEXT + len),
                        value.getBytes("UTF-8"));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    @Test
    public void testMediumTextAsString() throws Exception {
        for (int len = 24; len <= 255; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value);
                gen.close();
                _verifyBytes(out.toByteArray(),
                        CBORConstants.BYTE_STRING_1BYTE_LEN, (byte) len,
                        value.getBytes("UTF-8"));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    @Test
    public void testMediumTextAsCharArray() throws Exception {
        for (int len = 24; len <= 255; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value.toCharArray(), 0, len);
                gen.close();
                _verifyBytes(out.toByteArray(),
                        CBORConstants.BYTE_STRING_1BYTE_LEN, (byte) len,
                        value.getBytes("UTF-8"));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    private String generateAsciiString(int len) {
        StringBuilder sb = new StringBuilder(len);
        while (--len >= 0) {
            sb.append((char) ('0' + (len % 10)));
        }
        return sb.toString();
    }

    private void _verifyString(byte[] encoded, String value) throws Exception
    {
        try (JsonParser p = cborParser(encoded)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(value, p.getString());
            assertNull(p.nextToken());
        }
    }
}
