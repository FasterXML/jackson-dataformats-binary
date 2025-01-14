package com.fasterxml.jackson.dataformat.ion.tofix;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.ion.testutil.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// For [dataformats-binary#245]: no pretty-printing for textual format
public class PrettyPrintWriteTest
{
    @JsonPropertyOrder({ "x", "y" })
    static class Point {
        public int x = 1;
        public int y = 2;
    }

    @JacksonTestFailureExpected
    @Test
    public void testBasicPrettyPrintTextual() throws Exception
    {
        IonObjectMapper mapper = IonObjectMapper.builderForTextualWriters().build();
        assertEquals("{\n  x:1,\n  y:2\n}",
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Point()));
    }

    // and with binary format, should simply be no-op
    @Test
    public void testIgnorePrettyPrintForBinary() throws Exception
    {
        IonObjectMapper mapper = IonObjectMapper.builderForBinaryWriters().build();
        byte[] encoded = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Point());
        assertNotNull(encoded);
    }
}
