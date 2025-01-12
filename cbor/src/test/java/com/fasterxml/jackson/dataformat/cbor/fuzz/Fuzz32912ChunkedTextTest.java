package com.fasterxml.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class Fuzz32912ChunkedTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testInvalidShortText() throws Exception
    {
        final byte[] input = new byte[] {
                0x7F, 0x61,
                (byte) 0xF3, 0x61
        };

        try (JsonParser p = MAPPER.createParser(input)) {
            // Won't fail immediately
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getText();
                fail("Should not get String value but exception, got: ["+str+"]");
            } catch (JsonEOFException e) {
                verifyException(e, "Unexpected end-of-input in VALUE_STRING");
            }
        }
    }
}
