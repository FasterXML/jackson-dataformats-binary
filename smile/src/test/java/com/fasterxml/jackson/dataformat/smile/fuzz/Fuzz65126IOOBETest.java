package com.fasterxml.jackson.dataformat.smile.fuzz;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class Fuzz65126IOOBETest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // [dataformats-binary#426]
    public void testInvalidIOOBE() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-smile-65126.smile");
        try (JsonParser p = MAPPER.createParser(input)) {
            try {
                p.getTextOffset();
                p.nextTextValue();
                p.nextTextValue();
            } catch (StreamReadException e) {
                verifyException(e, "Invalid text length");
            }
        }
    }
}
