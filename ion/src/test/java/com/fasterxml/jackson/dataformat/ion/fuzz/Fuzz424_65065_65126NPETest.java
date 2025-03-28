package com.fasterxml.jackson.dataformat.ion.fuzz;

import java.io.ByteArrayInputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#424]
public class Fuzz424_65065_65126NPETest
{
    private final IonObjectMapper MAPPER = IonObjectMapper.builder().build();

    @Test
    public void testFuzz65065() throws Exception {
       try {
           byte[] bytes = {(byte) -32, (byte) 1, (byte) 0, (byte) -22, (byte) 123, (byte) -112};
           MAPPER.readTree(new ByteArrayInputStream(bytes));
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
       }
    }

    @Test
    public void testFuzz65126() throws Exception {
       try {
           byte[] bytes = {(byte) 1, (byte) 0};
           MAPPER.createParser(bytes).getDecimalValue();
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Current token (null) not numeric"));
       }
    }
}
