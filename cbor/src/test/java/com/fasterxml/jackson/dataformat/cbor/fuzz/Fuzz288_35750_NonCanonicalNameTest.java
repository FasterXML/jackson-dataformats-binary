package com.fasterxml.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class Fuzz288_35750_NonCanonicalNameTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#288]: non-canonical representation for length of 0
    // causing ArrayOutOfBoundsException
    public void testInvalidLongName() throws Exception
    {
        final byte[] input = new byte[] {
                (byte) 0x8A,
                (byte) 0xAD, 0x7A, 0x00,
                0x00, 0x00, 0x00
        };

        try (JsonParser p = MAPPER.createParser(input)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        }
    }
}
