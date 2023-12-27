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

// [dataformats-binary#434
public class Fuzz434_65268_65274_NPETest
{
    @Test
    public void testFuzz65268() throws Exception {
        final IonFactory factory =
            IonFactory.builderForTextualWriters().build();
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

    @Test
    public void testFuzz65274() throws Exception {
        final ObjectMapper MAPPER =
            IonObjectMapper.builder(
                    IonFactory.builderForBinaryWriters()
                        .enable(IonParser.Feature.USE_NATIVE_TYPE_ID)
                        .build())
                 .build();

        try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65274.ion")) {
           byte[] invalid = new byte[in.available()];
           new DataInputStream(in).readFully(invalid);
           try (JsonParser p = MAPPER.getFactory().createParser(new ByteArrayInputStream(invalid))) {
               MAPPER.readTree(p);
           }
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("not integer"));
       }
    }
}
