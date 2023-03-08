package com.fasterxml.jackson.dataformat.cbor.fuzz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import java.io.IOException;
import java.io.InputStream;

public class Fuzz35979ShortTextTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testInvalidShortText() throws Exception
    {
        try (InputStream is = Fuzz35979ShortTextTest.class.getResourceAsStream(
                "/data/clusterfuzz-testcase-minimized-CborFuzzer-5666082229714944")) {
            try (JsonParser p = MAPPER.createParser(is)) {
                // Won't fail immediately
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                try {
                    String str = p.getText();
                    fail("Should not get String value but exception, got: ["+str+"]");
                } catch (IOException e) {
                    verifyException(e, "Failed to parse CBOR input");
                }
            }
        }
    }
}
