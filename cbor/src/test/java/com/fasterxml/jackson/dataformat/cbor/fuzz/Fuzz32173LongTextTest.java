package com.fasterxml.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class Fuzz32173LongTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testTruncatedLongText() throws Exception
    {
        final byte[] input = new byte[] {
                0x7A, // Text value
                0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF // length: Integer.MAX_VALUE
        };
        try {
            /*JsonNode root =*/ MAPPER.readTree(input);
            fail("Should not pass, invalid content");
        } catch (JsonEOFException e) {
            verifyException(e, "Unexpected end-of-input in VALUE_STRING");
        }
    }
}
