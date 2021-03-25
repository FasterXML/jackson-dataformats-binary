package com.fasterxml.jackson.dataformat.smile.fuzz;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

// For [dataformats-binary#265]
public class FuzzXXXXX_7BitBinaryTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Test with maximum declared payload size -- CF-32377
    public void testInvalid7BitBinary() throws Exception
    {
        final byte[] input0 = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                (byte) 0xE8, // binary, 7-bit encoded
                0x0F, 0x7E, 0x20,
                0x20, (byte) 0xFF, // 5 byte VInt for 0x7fe4083f (close to Integer.MAX_VALUE)
        };

        // Let's expand slightly to avoid too early detection, ensure that we are
        // not only checking completely missing payload or such
        final byte[] input = Arrays.copyOf(input0, 65 * 1024);

        try (ByteArrayInputStream bytes = new ByteArrayInputStream(input)) {
            try {
            /*JsonNode root =*/ MAPPER.readTree(bytes);
            } catch (JsonEOFException e) {
                verifyException(e, "Unexpected end-of-input for Binary value (7-bit)",
                        "expected 2145650751 bytes  (from 2452172287 encoded)",
                        "only decoded 58226");
            }
        }
    }
}
