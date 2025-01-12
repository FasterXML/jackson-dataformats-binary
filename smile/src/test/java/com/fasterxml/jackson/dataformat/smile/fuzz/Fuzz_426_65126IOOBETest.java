package com.fasterxml.jackson.dataformat.smile.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class Fuzz_426_65126IOOBETest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // [dataformats-binary#426]
    @Test
    public void testInvalidIOOBE() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-smile-65126.smile");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertNull(p.nextTextValue());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.currentToken());
            try {
                p.nextTextValue();
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid content: invalid 7-bit binary encoded byte length");
            }
        }
    }
}
