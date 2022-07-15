package com.fasterxml.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.exc.UnexpectedEndOfInputException;

import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class Fuzz32173LongTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testTruncatedLongText() throws Exception
    {
        final byte[] input = new byte[] {
                0x7A, // Text value
                0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF // length: Integer.MAX_VALUE
        };
        try {
            /*JsonNode root =*/ MAPPER.readTree(input);
            fail("Should not pass, invalid content");
        } catch (UnexpectedEndOfInputException e) {
            verifyException(e, "Unexpected end-of-input in VALUE_STRING");
        }
    }
}
