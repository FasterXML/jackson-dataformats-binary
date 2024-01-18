package com.fasterxml.jackson.dataformat.cbor.fuzz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

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
                p.nextToken();
                p.getTextLength();
                fail("Should not reach here (invalid input)");
            } catch (StreamReadException e) {
                verifyException(e, "Requested length too long.");
            }
        }
    }
}
