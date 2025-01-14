package tools.jackson.dataformat.smile.fuzz;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.fail;

// For https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35932
public class SmileFuzz_291_35932_TruncUTF8NameTest extends BaseTestForSmile
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
            verifyException(e, "Truncated UTF-8 character in Short Unicode Name (36 bytes)");
        }
    }
}
