package tools.jackson.dataformat.smile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import tools.jackson.core.JsonParser;

import static org.junit.Assert.assertArrayEquals;

// for [jackson-core#730]
public class FloatPrecisionTest extends BaseTestForSmile
{
    // for [jackson-core#730]
    public void testFloatRoundtrips() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, true);
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

        JsonParser parser = _smileParser(expected);
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        SmileGenerator gen2 = _smileGenerator(out2, true);
        parser.nextToken();
        gen2.copyCurrentStructure(parser);
        gen2.close();
        byte[] actual = out2.toByteArray();
        assertArrayEquals(expected, actual);
    }
}
