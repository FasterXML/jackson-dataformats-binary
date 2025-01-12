package com.fasterxml.jackson.dataformat.ion.fuzz;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.*;

// [dataformats-binary#417]
public class Fuzz417_64721InvalidIonTest
{
    enum EnumFuzz {
        A, B, C, D, E;
    }

    @Test
    public void testFuzz64721AssertionException() throws Exception {
       IonFactory f = IonFactory
                          .builderForBinaryWriters()
                          .enable(IonParser.Feature.USE_NATIVE_TYPE_ID)
                          .build();
       IonObjectMapper mapper = IonObjectMapper.builder(f).build();
       try {
           mapper.readValue("$0/", EnumFuzz.class);
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
       }
    }
}
