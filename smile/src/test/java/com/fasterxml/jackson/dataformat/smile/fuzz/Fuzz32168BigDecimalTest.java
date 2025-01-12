package com.fasterxml.jackson.dataformat.smile.fuzz;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// For [dataformats-binary#257]
public class Fuzz32168BigDecimalTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    @Test
    public void testInvalidBigDecimal() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                0x2A, // BigDecimal
                (byte) 0xBF, // scale: -32
                (byte) 0x80 // length: 0 (invalid
        };
        JsonNode root = MAPPER.readTree(input);
        assertTrue(root.isNumber());
        assertTrue(root.isBigDecimal());
        assertEquals(BigDecimal.ZERO, root.decimalValue());
    }
}
