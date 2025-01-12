package com.fasterxml.jackson.dataformat.cbor;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadCapability;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FormatDefaultsTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @Test
    public void testParserDefaults() throws Exception
    {
        try (JsonParser p = MAPPER.createParser(new byte[4])) {
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            assertTrue(p.getReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }
    }
}
