package com.fasterxml.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// @since 2.12
public class SimpleValuesTest extends CBORTestBase
{
    private final CBORFactory CBOR_F = new CBORFactory();

    @Test
    public void testTinySimpleValues() throws Exception
    {
        // Values 0..19 are unassigned, valid to encounter
        for (int v = 0; v <= 19; ++v) {
            byte[] doc = new byte[1];
            doc[0] = (byte) (CBORConstants.PREFIX_TYPE_MISC + v);
            try (JsonParser p = CBOR_F.createParser(doc)) {
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
            try (JsonParser p = CBOR_F.createParser(doc)) {
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
            try (JsonParser p = CBOR_F.createParser(doc)) {
                try {
                    p.nextToken();
                    fail("Should not pass");
                } catch (JsonParseException e) {
                    verifyException(e, "Invalid second byte for simple value:");
                    verifyException(e, "0x"+Integer.toHexString(v));
                }
            }
        }
    }
}
