package com.fasterxml.jackson.dataformat.smile.fuzz;

import com.fasterxml.jackson.core.exc.StreamReadException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

// For [dataformats-binary#257]
public class Fuzz32168BigDecimalTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Payload:
    public void testInvalidBigDecimal() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                0x2A, // BigDecimal
                (byte) 0xBF, // scale: -32
                (byte) 0x80 // length: 0 (invalid
        };
        try {
            /*JsonNode root =*/ MAPPER.readTree(input);
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Invalid encoding of `BigDecimal` value: length 0");
        }
    }
}
