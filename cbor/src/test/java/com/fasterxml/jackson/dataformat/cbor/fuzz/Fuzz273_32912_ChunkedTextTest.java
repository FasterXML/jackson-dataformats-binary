package com.fasterxml.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class Fuzz273_32912_ChunkedTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#273]
    // (see https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=32912)
    @Test
    public void testChunkedWithUTF8_4Bytes_v2() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0x7F, // text, chunked (length marker 0x1F)
                0x61, // text segment of 1 bytes.
                (byte) 0xF3, // First byte of 4-byte UTF-8 character
                0x61 // (invalid) second byte of 4-byte UTF-8 character
        };

        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                p.getText();
                fail("Should not pass, invalid content");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input in VALUE_STRING");
            }
        }
    }
}
