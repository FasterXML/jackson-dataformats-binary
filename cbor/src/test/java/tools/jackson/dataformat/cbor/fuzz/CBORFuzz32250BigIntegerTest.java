package tools.jackson.dataformat.cbor.fuzz;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CBORFuzz32250BigIntegerTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testInvalidShortText() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0xC3,
                0x5F, (byte) 0xFF
        };
        JsonNode root = MAPPER.readTree(input);
        assertTrue(root.isNumber());
        assertTrue(root.isBigInteger());
        assertEquals(BigInteger.ZERO, root.bigIntegerValue());
    }
}
