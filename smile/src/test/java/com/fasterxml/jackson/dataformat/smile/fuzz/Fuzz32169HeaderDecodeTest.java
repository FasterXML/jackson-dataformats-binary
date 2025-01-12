package com.fasterxml.jackson.dataformat.smile.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.fail;

public class Fuzz32169HeaderDecodeTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Payload:
    @Test
    public void testInvalidHeader() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x20 // (broken) smile signature
        };
        try {
            /*JsonNode root =*/ MAPPER.readTree(input);
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Malformed content: signature not valid, starts with 0x3a but");
        }
    }
}
