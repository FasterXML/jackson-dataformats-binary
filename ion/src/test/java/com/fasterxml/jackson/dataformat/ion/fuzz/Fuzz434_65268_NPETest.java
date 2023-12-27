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

// [dataformats-binary#434
public class Fuzz434_65268_NPETest
{
    private final IonFactory factory =
        IonFactory.builderForTextualWriters().build();

    @Test
    public void testFuzz65268() throws Exception {
       try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65268.ion")) {
           try (JsonParser p = factory.createParser(in)) {
               p.nextToken();
               p.getText();
               p.nextTextValue();
               p.getNumberType();
           }
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("not integer"));
       }
    }
}
