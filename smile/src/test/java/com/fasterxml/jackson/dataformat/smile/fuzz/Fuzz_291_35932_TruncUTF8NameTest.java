package com.fasterxml.jackson.dataformat.smile.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

// For https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35932
public class Fuzz_291_35932_TruncUTF8NameTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Test with maximum declared payload size -- CF-32377
    public void testInvalid7BitBinary() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-smile-35932.smile");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextToken();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Truncated UTF-8 character in Short Unicode Name (36 bytes)");
        }
    }
}
