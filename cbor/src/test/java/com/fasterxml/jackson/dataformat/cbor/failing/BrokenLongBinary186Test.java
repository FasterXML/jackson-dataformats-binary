package com.fasterxml.jackson.dataformat.cbor.failing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORConstants;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

// Mostly for [dataformats-binary#186]: corrupt encoding indicating humongous payload
public class BrokenLongBinary186Test extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#186]
    public void testCorruptVeryLongBinary() throws Exception
    {
        // Let's do 999,999,999 bytes to likely trigger failure
        final int LONG_LEN = 999_999_999;
        byte[] DOC = new byte[] {
                        (byte) (CBORConstants.PREFIX_TYPE_BYTES | CBORConstants.SUFFIX_UINT32_ELEMENTS),
                        (byte) (LONG_LEN >> 24),
                        (byte) (LONG_LEN >> 16),
                        (byte) (LONG_LEN >> 8),
                        (byte) LONG_LEN,
                        // but only include 2 bytes
                        0, 0
        };
        JsonParser p = MAPPER.createParser(DOC);
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        try {
            p.getBinaryValue();
            fail("Should fail");
        } catch (JsonProcessingException e) {
//            e.printStackTrace();

            // 01-Dec-2020, tatu: Need to decide what kind of exception should be
            //    produced...
            verifyException(e, "foobar");
        }
    }
}
