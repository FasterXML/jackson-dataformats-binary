package com.fasterxml.jackson.dataformat.ion.fuzz;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.dataformat.ion.*;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("resource")
public class Fuzz64721InvalidIonTest
{
    @Test(expected = JsonParseException.class)
    public void testFuzz64721AssertionException() throws IOException {
       IonFactory f = IonFactory
                          .builderForBinaryWriters()
                          .enable(IonParser.Feature.USE_NATIVE_TYPE_ID)
                          .build();
       IonObjectMapper mapper = IonObjectMapper.builder(f).build();
       mapper.readValue("$0/", EnumFuzz.class);
    }

    private static enum EnumFuzz {
        A, B, C, D, E;
    }
}
