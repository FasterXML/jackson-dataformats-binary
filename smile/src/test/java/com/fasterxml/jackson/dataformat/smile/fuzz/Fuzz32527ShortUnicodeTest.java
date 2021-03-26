package com.fasterxml.jackson.dataformat.smile.fuzz;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals(6, p.currentName().length());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String text = p.getText();
                fail("Should have failed, instead decoded String of "+text.length()+" chars");
            } catch (StreamReadException e) {
                verifyException(e, "Truncated UTF-8 character in Short Unicode String");
                verifyException(e, "(12 bytes)");
                verifyException(e, "byte 0xE5 at offset #10 indicated 2 more bytes needed");
            }
        }
    }
}
