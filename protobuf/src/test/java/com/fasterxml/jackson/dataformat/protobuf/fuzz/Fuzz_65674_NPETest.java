package com.fasterxml.jackson.dataformat.protobuf.fuzz;

import java.io.*;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.protobuf.*;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

public class Fuzz_65674_NPETest
{
    private final ProtobufMapper mapper =
        ProtobufMapper.builder(ProtobufFactory.builder().build()).build();

    @Test
    public void testFuzz65674NPE() throws Exception {
        final byte[] doc = new byte[0];
        try (ProtobufParser p = ((ProtobufMapper) mapper).getFactory().createParser(doc)) {
            p.setSchema(mapper.generateSchemaFor(Fuzz_65674_NPETest.class));
            p.nextTextValue();
            p.currentName();
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertTrue(e.getMessage().contains("No parent object found"));
        }
    }
}
