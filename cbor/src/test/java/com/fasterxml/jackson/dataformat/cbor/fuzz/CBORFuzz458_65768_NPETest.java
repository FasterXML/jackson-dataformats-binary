package com.fasterxml.jackson.dataformat.cbor.fuzz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.exc.StreamReadException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class CBORFuzz458_65768_NPETest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testInvalidText() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-65768.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            try {
                p.nextTextValue();
                p.getIntValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.nextTextValue();
                p.getFloatValue();
                p.getDecimalValue();
                fail("Should not reach here (invalid input)");
            } catch (StreamReadException e) {
                verifyException(e, "No more values");
            }
        }
    }
}
