package tools.jackson.dataformat.smile.fuzz;

import java.math.BigDecimal;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

// For [dataformats-binary#257]
public class Fuzz32168BigDecimalTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

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
