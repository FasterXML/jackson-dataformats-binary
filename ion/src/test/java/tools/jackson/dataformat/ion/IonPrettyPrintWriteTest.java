package tools.jackson.dataformat.ion;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.databind.SerializationFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// For [dataformats-binary#245]: no pretty-printing for textual format
public class IonPrettyPrintWriteTest
{
    @JsonPropertyOrder({ "x", "y" })
    static class Point {
        public int x = 1;
        public int y = 2;
    }

    private final IonObjectMapper TEXTUAL_MAPPER = IonObjectMapper.builder(IonFactory.forTextualWriters()).build();

    @Test
    public void prettyPrintTextual() throws Exception
    {
        final String EXP = "{\n  x:1,\n  y:2\n}";

        String ion = TEXTUAL_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Point());
        assertEquals(EXP, ion.trim());

        ion = TEXTUAL_MAPPER.writer()
                .with(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(new Point());
        assertEquals(EXP, ion.trim());

        IonObjectMapper mapper = TEXTUAL_MAPPER.rebuild()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        ion = mapper.writeValueAsString(new Point());
        assertEquals(EXP, ion.trim());

        // But also no indentation if not requested
        ion = mapper.writer()
                .without(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(new Point());
        assertEquals("{x:1,y:2}", ion.trim());
    }

    // and with binary format, should simply be no-op
    @Test
    public void prettyPrintIgnoredForBinary() throws Exception
    {
        IonObjectMapper mapper = IonObjectMapper.builder(IonFactory.forBinaryWriters()).build();
        byte[] encoded = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Point());
        assertNotNull(encoded);
    }
}
