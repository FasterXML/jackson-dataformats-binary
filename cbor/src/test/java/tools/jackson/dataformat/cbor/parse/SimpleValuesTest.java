package tools.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonParser.NumberType;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.dataformat.cbor.CBORConstants;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SimpleValuesTest extends CBORTestBase
{
    @Test
    public void testTinySimpleValues() throws Exception
    {
        // Values 0..19 are unassigned, valid to encounter
        for (int v = 0; v <= 19; ++v) {
            byte[] doc = new byte[1];
            doc[0] = (byte) (CBORConstants.PREFIX_TYPE_MISC + v);
            try (JsonParser p = cborParser(doc)) {
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                // exposes as `int`, fwtw
                assertEquals(NumberType.INT, p.getNumberType());
                assertEquals(v, p.getIntValue());
            }
        }
    }

    @Test
    public void testValidByteLengthMinimalValues() throws Exception {
        // Values 32..255 are unassigned, valid to encounter
        for (int v = 32; v <= 255; ++v) {
            byte[] doc = { (byte) (CBORConstants.PREFIX_TYPE_MISC + 24), (byte) v };
            try (JsonParser p = cborParser(doc)) {
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                // exposes as `int`, fwtw
                assertEquals(NumberType.INT, p.getNumberType());
                assertEquals(v, p.getIntValue());
            }
        }
    }

    @Test
    public void testInvalidByteLengthMinimalValues() throws Exception {
        // Values 0..31 are invalid for variant that takes 2 bytes...
        for (int v = 0; v <= 31; ++v) {
            byte[] doc = { (byte) (CBORConstants.PREFIX_TYPE_MISC + 24), (byte) v };
            try (JsonParser p = cborParser(doc)) {
                try {
                    p.nextToken();
                    fail("Should not pass");
                } catch (StreamReadException e) {
                    verifyException(e, "Invalid second byte for simple value:");
                    verifyException(e, "0x"+Integer.toHexString(v));
                }
            }
        }
    }
}
