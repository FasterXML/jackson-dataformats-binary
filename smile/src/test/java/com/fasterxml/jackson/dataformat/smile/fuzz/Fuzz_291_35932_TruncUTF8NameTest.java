package com.fasterxml.jackson.dataformat.smile.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.fail;

// For https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35932
public class Fuzz_291_35932_TruncUTF8NameTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Test with maximum declared payload size -- CF-32377
    @Test
    public void testInvalid7BitBinary() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-smile-35932.smile");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextToken();
            fail("Should not pass");
        } catch (StreamReadException e) {
            // [dataformats-binary#861]: continuation bytes now validated, so fails earlier
            verifyException(e, "Invalid UTF-8 middle byte 0xf6");
        }
    }
}
