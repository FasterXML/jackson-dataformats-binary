package com.fasterxml.jackson.dataformat.smile.fuzz;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class Fuzz_426_65126IOOBETest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // [dataformats-binary#426]
    public void testInvalidIOOBE() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-smile-65126.smile");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertNull(p.nextTextValue());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.currentToken());
            try {
//                byte[] b = p.getBinaryValue();
//                assertEquals(100, b.length);
                p.nextTextValue();
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid text length");
            }
        }
    }
}
