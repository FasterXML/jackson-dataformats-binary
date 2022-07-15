package com.fasterxml.jackson.dataformat.smile.fuzz;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class Fuzz32527ShortUnicodeTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // [dataformats-binary#266]
    public void testInvalidShortUnicode() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                (byte) 0xFA, // START_OBJECT
                (byte) 0xC8, // short-unicode-name: 10 bytes (0x8 + 2), 6 chars
                (byte) 0xC8, (byte) 0xC8,
                (byte) 0xC8, (byte) 0xC8, (byte) 0xC8, 0x00,
                0x00, (byte) 0xF3, (byte) 0xA0, (byte) 0x81,

                (byte) 0x8A, // short-unicode-value: 12 bytes (0xA + 2)
                0x00, 0x01, 0x00,
                0x00, 0x00, 0x01, 0x01,
                0x00, 0x00, 0x04, (byte) 0xE5,
                0x04
        };
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            // 08-Jul-2021, tatu: Used to fail later but after unrelated fix, fails here:
            try {
                p.nextToken();
                fail("Should have failed");
            } catch (StreamReadException e) {
                verifyException(e, "Truncated UTF-8 character in Short Unicode Name (10 bytes)");
            }
        }
    }
}
