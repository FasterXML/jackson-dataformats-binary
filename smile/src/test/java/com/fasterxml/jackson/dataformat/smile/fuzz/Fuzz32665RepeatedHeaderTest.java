package com.fasterxml.jackson.dataformat.smile.fuzz;

import java.io.ByteArrayOutputStream;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

// for [dataformats-binary#268]
public class Fuzz32665RepeatedHeaderTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    // for [dataformats-binary#268]
    public void testLongRepeatedHeaders() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16001);
        for (int i = 0; i < 10; ++i) {
            // repeat Smile header 10 times
            bytes.write(0x3A);
            bytes.write(0x29);
            bytes.write(0x0A);
            bytes.write(0x00);
        }

        // and then append "empty String" marker for funsies
        bytes.write(0x20);

        final byte[] DOC = bytes.toByteArray();

        try (JsonParser p = MAPPER.createParser(DOC)) {
            // Ideally would get 9 nulls but looks like at the beginning of stream
            // it will be one less (not so later on). Good enough given that there is
            // no real definition of handling here.
            for (int i = 0; i < 8; ++i) {
                JsonToken t = p.nextToken();
                if (t != null) {
                    fail("Failed at token #"+i+"; expected `null`, got: "+t);
                }
            }
            // and finally, empty String
            assertToken(JsonToken.VALUE_STRING, p.nextToken());

            // and then the "real" end of input
            assertNull(p.nextToken());
        }
    }
}
