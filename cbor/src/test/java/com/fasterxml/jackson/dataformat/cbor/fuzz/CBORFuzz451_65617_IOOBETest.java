package com.fasterxml.jackson.dataformat.cbor.fuzz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class CBORFuzz451_65617_IOOBETest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testInvalidText() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-65617.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            try {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                // Important: do not access String, force skipping
                p.nextToken();
                fail("Should not reach here (invalid input)");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid length indicator");
            }
        }
    }
}
