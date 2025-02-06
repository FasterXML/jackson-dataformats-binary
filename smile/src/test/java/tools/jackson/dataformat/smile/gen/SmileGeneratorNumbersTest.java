package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmileGeneratorNumbersTest
    extends BaseTestForSmile
{
    @Test
    public void testSmallInts() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeNumber(3);
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (0xC0 + SmileUtil.zigzagEncode(3)));

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(0);
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (0xC0 + SmileUtil.zigzagEncode(0)));

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(-6);
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (0xC0 + SmileUtil.zigzagEncode(-6)));

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(15);
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (0xC0 + SmileUtil.zigzagEncode(15)));

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(-16);
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (0xC0 + SmileUtil.zigzagEncode(-16)));
    }

    @Test
    public void testOtherInts() throws Exception
    {
    	// beyond tiny ints, 6-bit values take 2 bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeNumber(16);
        gen.close();
        assertEquals(2, out.toByteArray().length);

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(-17);
        gen.close();
        assertEquals(2, out.toByteArray().length);

        // and up to 13-bit values take 3 bytes
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(0xFFF);
        gen.close();
        assertEquals(3, out.toByteArray().length);

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(-4096);
        gen.close();
        assertEquals(3, out.toByteArray().length);

        // up to 20, 4 bytes... and so forth
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(0x1000);
        gen.close();
        assertEquals(4, out.toByteArray().length);

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(500000);
        gen.close();
        assertEquals(4, out.toByteArray().length);

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(Integer.MAX_VALUE);
        gen.close();
        assertEquals(6, out.toByteArray().length);

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(Integer.MIN_VALUE);
        gen.close();
        assertEquals(6, out.toByteArray().length);

        // up to longest ones, taking 11 bytes
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(Long.MAX_VALUE);
        gen.close();
        assertEquals(11, out.toByteArray().length);

        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(Long.MIN_VALUE);
        gen.close();
        assertEquals(11, out.toByteArray().length);
    }

    @Test
    public void testFloats() throws Exception
    {
        // float length is fixed, 6 bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator gen = _smileGenerator(out, false)) {
            gen.writeNumber(0.125f);
        }
        assertEquals(6, out.toByteArray().length);
    }

    @Test
    public void testDoubles() throws Exception
    {
        // double length is fixed, 11 bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator gen = _smileGenerator(out, false)) {
            gen.writeNumber(0.125);
        }
        assertEquals(11, out.toByteArray().length);
    }

    // [dataformats-binary#300]
    @Test
    public void testFloatUnusedBits() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator gen = _smileGenerator(out, false)) {
            gen.writeNumber(-0f);
        }
        byte[] encoded = out.toByteArray();
        assertEquals(6, encoded.length);
        assertEquals(0x28, encoded[0]); // type byte, float

        // From 0x80 0x00 0x00 0x00 (spread over 5 x 7bits)
        assertArrayEquals(new byte[] {
                0x08, 0x00, 0x00, 0x00, 0x00
        }, Arrays.copyOfRange(encoded, 1, encoded.length));
}

    // [dataformats-binary#300]
    @Test
    public void testDoubleUnusedBits() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator gen = _smileGenerator(out, false)) {
            gen.writeNumber(-0d);
        }
        byte[] encoded = out.toByteArray();
        assertEquals(11, encoded.length);
        assertEquals(0x29, encoded[0]); // type byte, double
        // From 0x80 0x00 0x00 0x00 ... 0x00 (spread over 10 x 7 bits)

        assertArrayEquals(new byte[] {
                0x01, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00
        }, Arrays.copyOfRange(encoded, 1, encoded.length));
    }

    // #16: Problems with 'Stringified' numbers
    @Test
    public void testNumbersAsString() throws Exception
    {
        ByteArrayOutputStream out;
        SmileGenerator gen;

        // first int
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber("15");
        gen.close();
        _verifyBytes(out.toByteArray(), (byte) (0xC0 + SmileUtil.zigzagEncode(15)));

        // then long. Note: cut-off to BigInteger not exact, so...
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber(String.valueOf(-1L + Integer.MIN_VALUE));
        gen.close();
        assertEquals(6, out.toByteArray().length);

        // and then just BigDecimal...
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNumber("-50.00000000125");
        gen.close();
        assertEquals(10, out.toByteArray().length);
    }
}
