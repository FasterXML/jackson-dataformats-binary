package com.fasterxml.jackson.dataformat.cbor.failing;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

// Reproduction for https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=32216
// (note: test here, since repro with CBOR encoded content, but actual fix
// will be in jackson-core)
public class Fuzz32216QuadSpillOverTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testSymbolTableOverflow() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzzer-jackson-32216.cbor");
        try {
            /*JsonNode root =*/ MAPPER.readTree(input);
            fail("Should not pass, symbol table overflow");
        } catch (JsonProcessingException e) {
            verifyException(e, "NOT REALLY THIS ONE");
        }
    }
}
