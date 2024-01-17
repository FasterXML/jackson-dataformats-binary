package com.fasterxml.jackson.dataformat.protobuf.fuzz;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.protobuf.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProtobufFuzz_65674_NPETest
{
    private final ProtobufMapper mapper = new ProtobufMapper();

    @Test
    public void testFuzz65674NPE() throws Exception {
        final byte[] doc = new byte[0];
        try (ProtobufParser p = (ProtobufParser) mapper.getFactory().createParser(doc)) {
            p.setSchema(mapper.generateSchemaFor(getClass()));
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertNull(p.nextToken());
        }
    }
}
