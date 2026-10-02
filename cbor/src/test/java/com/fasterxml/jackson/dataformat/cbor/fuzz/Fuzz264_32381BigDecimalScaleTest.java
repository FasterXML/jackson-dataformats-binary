package com.fasterxml.jackson.dataformat.cbor.fuzz;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
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
        // 02-Oct-2026: [dataformats-binary#842] Original fuzz input had exponent of
        //   -2^31, which is out of range (needs scale of 2^31); use exponent of +2^31
        //   instead, for the same extreme scale (Integer.MIN_VALUE)
        final byte[] input = new byte[] {
                (byte) 0xC4, // tag
                (byte) 0x82, 0x1A, (byte) 0x80,
                0, 0, 0, 0x0A
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

    // [dataformats-binary#842]: original fuzz input (exponent of -2^31) must now
    // fail cleanly, for both streaming and databind access
    @Test
    public void testOriginalFuzzInputFails() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0xC4, // tag
                (byte) 0x82, 0x3A, 0x7F,
                (byte) 0xFF, (byte) 0xFF, (byte)  0xFF, 0x0A
        };
        try (JsonParser p = MAPPER.createParser(input)) {
            p.nextToken();
            fail("Should not pass, got: "+p.getDecimalValue());
        } catch (StreamReadException e) {
            verifyException(e, "out of range for `BigDecimal`");
        }

        try {
            JsonNode root = MAPPER.readTree(input);
            fail("Should not pass, got: "+root);
        } catch (StreamReadException e) {
            verifyException(e, "out of range for `BigDecimal`");
        }
    }
}
