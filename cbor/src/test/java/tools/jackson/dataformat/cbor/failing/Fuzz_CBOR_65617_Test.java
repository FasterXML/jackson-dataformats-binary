package tools.jackson.dataformat.cbor.failing;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.CBORTestBase;

// Trying to reproduce
//
// https://bugs.chromium.org/p/oss-fuzz/issues/detail?65617
//
// but does not quite fail the way Fuzzer does (AIOOBE on nextToken when
// skipping VALUE_EMBEDDED_OBJECT
public class Fuzz_CBOR_65617_Test extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#???]
    //
    public void testFuzzCase65617() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-65617.cbor");
//        try (JsonParser p = MAPPER.createParser(new java.io.ByteArrayInputStream(input))) {
        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            // Should we access alleged byte[] or skip?
//            p.getBinaryValue();
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Invalid CBOR value token (first byte): 0x5d");
        }
    }
}
