package tools.jackson.dataformat.smile.async;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.*;

// Async variant of [dataformats-binary#257]: zero-length big number
// payload should decode as zero, same as with blocking parser
public class AsyncZeroLengthBigNumberTest extends AsyncTestBase
{
    private final SmileMapper F = new SmileMapper();

    @Test
    public void testZeroLengthBigInteger() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                0x26, // BigInteger
                (byte) 0x80 // length: 0
        };
        for (int bytesPerRead : new int[] { 1, 99 }) {
            AsyncReaderWrapper r = asyncForBytes(F, bytesPerRead, input, 0);
            try {
                assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
                assertEquals(JsonParser.NumberType.BIG_INTEGER, r.getNumberType());
                assertEquals(BigInteger.ZERO, r.getBigIntegerValue());
            } finally {
                r.close();
            }
        }
    }

    @Test
    public void testZeroLengthBigDecimal() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x29, 0x0A, 0x00, // smile signature
                0x2A, // BigDecimal
                (byte) 0xBF, // scale: -32
                (byte) 0x80 // length: 0
        };
        for (int bytesPerRead : new int[] { 1, 99 }) {
            AsyncReaderWrapper r = asyncForBytes(F, bytesPerRead, input, 0);
            try {
                assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
                assertEquals(JsonParser.NumberType.BIG_DECIMAL, r.getNumberType());
                assertEquals(BigDecimal.ZERO, r.getBigDecimalValue());
            } finally {
                r.close();
            }
        }
    }
}
