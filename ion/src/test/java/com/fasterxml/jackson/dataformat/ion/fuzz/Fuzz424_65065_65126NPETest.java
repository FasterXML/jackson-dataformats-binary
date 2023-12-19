package com.fasterxml.jackson.dataformat.ion.fuzz;

import java.io.ByteArrayInputStream;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

// [dataformats-binary#424]
public class Fuzz424_65065_65126NPETest
{
    @Test
    public void testFuzz65065() throws Exception {
       IonFactory f = IonFactory
                          .builderForBinaryWriters()
                          .build();

       IonObjectMapper mapper = IonObjectMapper.builder(f).build();

       try {
           byte[] bytes = {(byte) -32, (byte) 1, (byte) 0, (byte) -22, (byte) 123, (byte) -112};
           mapper.readTree(f.createParser(new ByteArrayInputStream(bytes)));
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Internal `IonReader` error"));
       }
    }

    @Test
    public void testFuzz65126() throws Exception {
       IonFactory f = IonFactory
                          .builderForBinaryWriters()
                          .build();

       try {
           byte[] bytes = {(byte) 1, (byte) 0};
           f.createParser(bytes).getDecimalValue();
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Current token (null) not numeric"));
       }
    }
}