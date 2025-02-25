package com.fasterxml.jackson.dataformat.protobuf.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProtobufFuzz_65674_NPETest
{
    private final ProtobufMapper MAPPER = new ProtobufMapper();

    @Test
    public void testFuzz65674NPE() throws Exception {
        final byte[] doc = new byte[0];
        try (JsonParser p = MAPPER.createParser(doc)) {
            p.setSchema(MAPPER.generateSchemaFor(getClass()));
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertNull(p.nextToken());
        }
    }
}
