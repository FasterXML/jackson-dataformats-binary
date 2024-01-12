package tools.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

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
    public void testInvalidTextValueWithBrokenUTF8() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-35979.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getText();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Malformed UTF-8 character at the end of a");
        }

    }
}
