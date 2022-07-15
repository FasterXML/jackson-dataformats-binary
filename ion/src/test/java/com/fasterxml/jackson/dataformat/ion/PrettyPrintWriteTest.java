package com.fasterxml.jackson.dataformat.ion;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.SerializationFeature;

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
        final String EXP = "{\n  x:1,\n  y:2\n}";

        IonObjectMapper mapper = IonObjectMapper.builder(IonFactory.forTextualWriters()).build();
        String ion = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Point());
        Assert.assertEquals(EXP, ion.trim());

        ion = mapper.writer()
                .with(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(new Point());
        Assert.assertEquals(EXP, ion.trim());

        // But also no indentation if not requested
        ion = mapper.writer()
                .writeValueAsString(new Point());
        Assert.assertEquals("{x:1,y:2}", ion.trim());
    }

    // and with binary format, should simply be no-op
    @Test
    public void testIgnorePrettyPrintForBinary() throws Exception
    {
        IonObjectMapper mapper = IonObjectMapper.builder(IonFactory.forBinaryWriters()).build();
        byte[] encoded = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Point());
        Assert.assertNotNull(encoded);
    }
}
