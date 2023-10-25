package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class GeneratorShortStringTest extends CBORTestBase
{
    public void testEmptyString() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CBORGenerator gen = cborGenerator(out)) {
            // First with String as input
            gen.writeString("");
        }
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_EMPTY_STRING);

        // then as char[]
        out = new ByteArrayOutputStream();
        try (CBORGenerator gen = cborGenerator(out)) {
            gen.writeString(new char[0], 0, 0);
        }
        _verifyBytes(out.toByteArray(), CBORConstants.BYTE_EMPTY_STRING);
    }

    public void testShortTextAsString() throws Exception {
        for (int len = 1; len <= 23; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value);
                gen.close();
                _verifyBytes(out.toByteArray(), (byte) (CBORConstants.PREFIX_TYPE_TEXT + len),
                        value.getBytes(StandardCharsets.UTF_8));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    public void testShortTextAsCharArray() throws Exception {
        for (int len = 1; len <= 23; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value.toCharArray(), 0, len);
                gen.close();
                _verifyBytes(out.toByteArray(), (byte) (CBORConstants.PREFIX_TYPE_TEXT + len),
                        value.getBytes(StandardCharsets.UTF_8));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    public void testMediumTextAsString() throws Exception {
        for (int len = 24; len <= 255; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value);
                gen.close();
                _verifyBytes(out.toByteArray(),
                        CBORConstants.BYTE_STRING_1BYTE_LEN, (byte) len,
                        value.getBytes(StandardCharsets.UTF_8));
                _verifyString(out.toByteArray(), value);
            }
        }
    }

    public void testMediumTextAsCharArray() throws Exception {
        for (int len = 24; len <= 255; ++len) {
            final String value = generateAsciiString(len);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator gen = cborGenerator(out)) {
                gen.writeString(value.toCharArray(), 0, len);
                gen.close();
                _verifyBytes(out.toByteArray(),
                        CBORConstants.BYTE_STRING_1BYTE_LEN, (byte) len,
                        value.getBytes(StandardCharsets.UTF_8));
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
            assertEquals(value, p.getText());
            assertNull(p.nextToken());
        }
    }
}
