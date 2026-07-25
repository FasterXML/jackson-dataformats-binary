package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

// for [jackson-core#730]
public class FloatPrecisionTest extends CBORTestBase
{
    // for [jackson-core#730]
    @Test
    public void testFloatRoundtrips() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeStartArray();

        gen.writeNumber(Float.MIN_VALUE);
        gen.writeNumber(0.0f);
        gen.writeNumber(Float.MAX_VALUE);

        gen.writeNumber(Double.MIN_VALUE);
        gen.writeNumber(0.0d);
        gen.writeNumber(Double.MAX_VALUE);

        gen.writeNumber(new BigDecimal("1e999"));
        gen.writeEndArray();
        gen.close();
        byte[] expected = out.toByteArray();

        CBORParser parser = cborParser(expected);
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        CBORGenerator gen2 = cborGenerator(out2);
        parser.nextToken();
        gen2.copyCurrentStructure(parser);
        gen2.close();
        byte[] actual = out2.toByteArray();
        assertArrayEquals(expected, actual);
    }
}
