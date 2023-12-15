package com.fasterxml.jackson.dataformat.ion.fuzz;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

// [dataformats-binary#417]
public class Fuzz420_65062_65083IOOBETest
{
    private void testIOOBE(byte[] array) throws Exception {
       IonFactory f = IonFactory
                          .builderForTextualWriters()
                          .enable(IonParser.Feature.USE_NATIVE_TYPE_ID)
                          .build();
       IonObjectMapper mapper = IonObjectMapper.builder(f).build();
       try {
           mapper.readTree(mapper.getFactory().createParser(new ByteArrayInputStream(array)));
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Invalid type ID"));
       }
    }

    @Test
    public void testFuzz6506265083IOOBE() throws Exception {
       byte[] byteArray = Files.readAllBytes(Paths.get("tc1"));
       testIOOBE(byteArray);
    }
}
