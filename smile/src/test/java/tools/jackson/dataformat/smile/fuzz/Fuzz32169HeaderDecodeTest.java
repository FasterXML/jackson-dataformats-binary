package tools.jackson.dataformat.smile.fuzz;

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

//For [dataformats-binary#258]
public class Fuzz32169HeaderDecodeTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // Payload:
    public void testInvalidHeader() throws Exception
    {
        final byte[] input = new byte[] {
                0x3A, 0x20 // (broken) smile signature
        };
        try {
            /*JsonNode root =*/ MAPPER.readTree(input);
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Malformed content: signature not valid, starts with 0x3a but");
        }
    }
}
