package tools.jackson.dataformat.smile.fuzz;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class SmileFuzz_426_65126IOOBETest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // [dataformats-binary#426]
    @Test
    public void testInvalidIOOBE() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-smile-65126.smile");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertNull(p.nextStringValue());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.currentToken());
            try {
                p.nextStringValue();
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid content: invalid 7-bit binary encoded byte length");
            }
        }
    }
}
