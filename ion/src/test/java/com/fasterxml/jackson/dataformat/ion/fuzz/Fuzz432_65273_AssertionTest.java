package com.fasterxml.jackson.dataformat.ion.fuzz;

import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

// [dataformats-binary#4432
public class Fuzz432_65273_AssertionTest
{
    private final IonFactory factory =
        IonFactory.builderForTextualWriters().build();

    @Test
    public void testFuzz65273() throws Exception {
       try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65273.ion")) {
           try (JsonParser p = factory.createParser(in)) {
               p.nextToken();
           }
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode; underlying failure"));
       }
    }
}
