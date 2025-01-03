package tools.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

public class CBORFuzz272_32722_ChunkedTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#272]
    public void testChunkedWithUTF8_4Bytes() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0x7F, // text, chunked (length marker 0x1F)
                0x60, // text segment of 0 bytes. Legal but weird
                (byte) 0xF0, // "simple value" 16, reported as "int" 16.
                0x70 // ... whatever this would be, fuzzer playing with stuff
        };

        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                p.getString();
                fail("Should not pass, invalid content");
            } catch (StreamReadException e) {
                verifyException(e, "Mismatched chunk in chunked content");
                verifyException(e, "(byte 0xF0)");
            }
        }
    }
}
