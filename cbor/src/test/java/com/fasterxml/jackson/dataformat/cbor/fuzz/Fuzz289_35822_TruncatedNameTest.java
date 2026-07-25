package com.fasterxml.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class Fuzz289_35822_TruncatedNameTest extends CBORTestBase
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
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Truncated UTF-8");
                verifyException(e, "byte 0xE6 at offset #0 indicated 2 more bytes needed");
            }
        }
    }
}
