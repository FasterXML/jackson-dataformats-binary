package tools.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class CBORFuzz289_35822_TruncatedNameTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // As per https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35822
    // ArrayIndexOutOfBoundsException when 2 out of 3 bytes available before
    // end-of-input
    @Test
    public void testInvalidSplitUtf8Unit() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0xA6, // Object, 6 entries
                0x78, 0x02, // String (key), length 2 (non-canonical)
                (byte) 0xE6, (byte) 0x8B // broken UTF-8 codepoint
        };

        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            try {
                assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Truncated UTF-8");
                verifyException(e, "byte 0xE6 at offset #0 indicated 2 more bytes needed");
            }
        }
    }
}
