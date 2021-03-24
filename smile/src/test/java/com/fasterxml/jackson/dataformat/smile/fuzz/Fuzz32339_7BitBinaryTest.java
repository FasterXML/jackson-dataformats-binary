package com.fasterxml.jackson.dataformat.smile.fuzz;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

//For [dataformats-binary#263]
public class Fuzz32339_7BitBinaryTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Test with negative length indicator (due to overflow) -- CF-32339
    public void testInvalid7BitBinary() throws Exception
    {
        final byte[] input0 = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                (byte) 0xE8, // binary, 7-bit encoded
                0x35, 0x20, 0x20,
                0x20, (byte) 0xFF // 5 byte VInt for 0x7fe4083f (close to Integer.MAX_VALUE)
        };

        // Let's expand slightly to avoid too early detection, ensure that we are
        // not only checking completely missing payload or such
        final byte[] input = Arrays.copyOf(input0, 65 * 1024);

        try (ByteArrayInputStream bytes = new ByteArrayInputStream(input)) {
            try {
            /*JsonNode root =*/ MAPPER.readTree(bytes);
            } catch (StreamReadException e) {
                verifyException(e, "Overflow in VInt (current token VALUE_EMBEDDED_OBJECT");
                verifyException(e, "1st byte (0x35)");
            }
        }
    }
}
