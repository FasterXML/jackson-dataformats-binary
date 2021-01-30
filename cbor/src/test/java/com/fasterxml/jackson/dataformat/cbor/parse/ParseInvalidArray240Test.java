package com.fasterxml.jackson.dataformat.cbor.parse;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class ParseInvalidArray240Test extends CBORTestBase
{
    private final CBORFactory F = cborFactory();

    // [dataformats-binary#240]
    public void test1ByteIncompleteArray() throws Exception
    {
        final byte[] input = {  (byte) 0x84 };
        try (CBORParser p = cborParser(F, input)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            try {
                p.nextToken();
                fail("Should NOT pass");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input: expected close marker for Array");
            }
        }
    }
}
