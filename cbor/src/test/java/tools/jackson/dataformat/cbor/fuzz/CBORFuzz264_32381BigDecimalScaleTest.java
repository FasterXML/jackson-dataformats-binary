package tools.jackson.dataformat.cbor.fuzz;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.JsonParser.NumberType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#264]
public class CBORFuzz264_32381BigDecimalScaleTest extends CBORTestBase
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
