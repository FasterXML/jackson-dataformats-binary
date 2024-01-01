package com.fasterxml.jackson.dataformat.ion.fuzz;

import java.io.*;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

// [dataformats-binary#437]
public class Fuzz437_65452_NPETest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    @Test
    public void testFuzz65452Eof() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65452.ion")) {
            try (JsonParser p = ION_MAPPER.createParser(in)) {
                assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
            }
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
        }
    }
}
