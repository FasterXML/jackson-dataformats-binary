package com.fasterxml.jackson.dataformat.cbor.failing;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class Fuzz_35979_StringValueTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#316]
    // https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35979
    public void testInvalidTextValueWithBrokenUTF8() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-35979.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getText();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Truncated UTF-8 character in Short Unicode Name (36 bytes)");
        }

    }
}
