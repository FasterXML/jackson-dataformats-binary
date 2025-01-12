package com.fasterxml.jackson.dataformat.ion.failing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

// For [dataformats-binary#245]: no pretty-printing for textual format
public class PrettyPrintWriteTest
{
    @JsonPropertyOrder({ "x", "y" })
    static class Point {
        public int x = 1;
        public int y = 2;
    }

    @Test
    public void testBasicPrettyPrintTextual() throws Exception
    {
        IonObjectMapper mapper = IonObjectMapper.builderForTextualWriters().build();
        Assert.assertEquals("{\n  x:1,\n  y:2\n}",
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Point()));
    }

    // and with binary format, should simply be no-op
    @Test
    public void testIgnorePrettyPrintForBinary() throws Exception
    {
        IonObjectMapper mapper = IonObjectMapper.builderForBinaryWriters().build();
        byte[] encoded = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Point());
        Assert.assertNotNull(encoded);
    }
}
