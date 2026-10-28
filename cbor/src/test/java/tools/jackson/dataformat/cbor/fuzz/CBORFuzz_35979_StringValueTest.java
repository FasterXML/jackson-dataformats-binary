package tools.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.fail;

public class CBORFuzz_35979_StringValueTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#316]
    // https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35979
    //
    // Problem is decoding of a 256 byte malformed String, last byte of
    // which indicates multi-byte UTF-8 character; decoder does not verify
    // there are more bytes available. If at end of buffer, hits ArrayIndex;
    // otherwise would return corrupt character with data past content end
    @Test
    public void testInvalidTextValueWithBrokenUTF8() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-35979.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getString();
            fail("Should not pass");
        } catch (StreamReadException e) {
            // [dataformats-binary#860]: last byte (0xF7) is now rejected as invalid
            //   lead byte, before truncation check
            verifyException(e, "Truncated UTF-8 character in Unicode String value (256 bytes)",
                    "Invalid UTF-8 start byte 0xf7");
        }

    }
}
