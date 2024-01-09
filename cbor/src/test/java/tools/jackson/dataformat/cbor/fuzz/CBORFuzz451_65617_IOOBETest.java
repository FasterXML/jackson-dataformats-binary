package tools.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.CBORTestBase;

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
