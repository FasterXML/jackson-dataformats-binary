package tools.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.CBORTestBase;

public class CBORFuzz464_65722_IOOBETest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testInvalidText() throws Exception
    {
        final byte[] input = {
            (byte)-60, (byte)-49, (byte)122, (byte)127, (byte)-1,
            (byte)-1, (byte)-1, (byte)15, (byte)110
        };
        try (JsonParser p = MAPPER.createParser(input)) {
            try {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                // oddly enough `getText()` didn't do it but this:
                p.getStringLength();
                fail("Should not reach here (invalid input)");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input in VALUE_STRING");
            }
        }
    }
}
