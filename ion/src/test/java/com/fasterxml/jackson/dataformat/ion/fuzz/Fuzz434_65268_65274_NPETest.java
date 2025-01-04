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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

// [dataformats-binary#434]
public class Fuzz434_65268_65274_NPETest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    // Test that used to fail on "getNumberType()" for `JsonToken.VALUE_NULL`
    @Test
    public void testFuzz65268() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-65268.ion");
        try (InputStream in = new ByteArrayInputStream(doc)) {
           try (JsonParser p = ION_MAPPER.createParser(in)) {
               assertEquals(JsonToken.VALUE_STRING, p.nextToken());
               p.getText();
               assertNull(p.nextTextValue());
               assertEquals(JsonToken.VALUE_NULL, p.currentToken());
               assertNull(p.getNumberType());
           }
       }
    }

    @Test
    public void testFuzz65274Malformed() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-65274.ion");
        try {
            ION_MAPPER.readTree(new ByteArrayInputStream(doc));
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt Number value to decode"));
        }
    }

    @Test
    public void testFuzz65274Eof() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65274.ion")) {
            ION_MAPPER.readTree(in);
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt Number value to decode"));
        }
    }
}
