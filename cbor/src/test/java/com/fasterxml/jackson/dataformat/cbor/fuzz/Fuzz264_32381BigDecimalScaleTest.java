package com.fasterxml.jackson.dataformat.cbor.fuzz;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#264]
public class Fuzz264_32381BigDecimalScaleTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testInvalidBigDecimal() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0xC4, // tag
                (byte) 0x82, 0x3A, 0x7F,
                (byte) 0xFF, (byte) 0xFF, (byte)  0xFF, 0x0A
        };
        BigDecimal streamingValue;
        // Access via regular read worked already
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, p.getNumberType());
            streamingValue = p.getDecimalValue();
            assertNotNull(streamingValue);
        }

        // But this failed, due to (default) normalization of BigDecimal values
        JsonNode root = MAPPER.readTree(input);
        assertTrue(root.isNumber());
        assertTrue(root.isBigDecimal());

        BigDecimal treeValue = root.decimalValue();

        assertEquals(streamingValue, treeValue);
    }
}
