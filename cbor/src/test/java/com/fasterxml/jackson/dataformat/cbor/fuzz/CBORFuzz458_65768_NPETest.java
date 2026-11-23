package com.fasterxml.jackson.dataformat.cbor.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class CBORFuzz458_65768_NPETest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testInvalidText() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-65768.cbor");
        // Since [dataformats-binary#912] 32-bit Tag value 0xBFDADA9E is rejected (was
        // read as negative int): change it to 0x3FDADA9E to reach the original failure
        assertEquals((byte) 0xBF, input[0x0D]);
        input[0x0D] = 0x3F;
        try (JsonParser p = MAPPER.createParser(input)) {
            try {
                assertNull(p.nextTextValue());
                assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.currentToken());
                assertEquals(0, p.getIntValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.currentToken());
                p.getFloatValue();
                p.getDecimalValue();
                fail("Should not reach here (invalid input)");
            } catch (StreamReadException e) {
                verifyException(e, "Current token (VALUE_EMBEDDED_OBJECT) not numeric");
            }
        }
    }

    @Test
    public void testInvalidTag() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-65768.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            // Float, two empty Maps, then tagged value
            while (p.nextToken() != null) { }
            fail("Should not reach here (invalid input)");
        } catch (StreamReadException e) {
            verifyException(e, "Illegal Tag value: 3218791070");
        }
    }
}
