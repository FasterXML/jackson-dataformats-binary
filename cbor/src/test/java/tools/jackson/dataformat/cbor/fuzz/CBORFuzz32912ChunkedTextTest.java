package tools.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.UnexpectedEndOfInputException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class CBORFuzz32912ChunkedTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testInvalidShortText() throws Exception
    {
        final byte[] input = new byte[] {
                0x7F, 0x61,
                (byte) 0xF3, 0x61
        };

        try (JsonParser p = MAPPER.createParser(input)) {
            // Won't fail immediately
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getString();
                fail("Should not get String value but exception, got: ["+str+"]");
            } catch (UnexpectedEndOfInputException e) {
                verifyException(e, "Unexpected end-of-input in VALUE_STRING");
            }
        }
    }
}
