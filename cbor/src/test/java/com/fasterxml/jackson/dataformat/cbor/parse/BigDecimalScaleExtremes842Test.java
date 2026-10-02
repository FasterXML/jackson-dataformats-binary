package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#842]: wrong exponent for BigDecimal with scale Integer.MIN_VALUE
public class BigDecimalScaleExtremes842Test extends CBORTestBase
{
    @Test
    public void testWriteMinIntScale() throws Exception
    {
        // 1E+2147483648: exponent +2^31 as uint32
        assertArrayEquals(new byte[] {
                (byte) 0xC4, (byte) 0x82, 0x1A, (byte) 0x80, 0, 0, 0, 0x01
        }, _write(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE)));
    }

    @Test
    public void testWriteOtherExtremeScales() throws Exception
    {
        assertArrayEquals(new byte[] {
                (byte) 0xC4, (byte) 0x82, 0x1A, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01
        }, _write(new BigDecimal(BigInteger.ONE, -Integer.MAX_VALUE)));
        assertArrayEquals(new byte[] {
                (byte) 0xC4, (byte) 0x82, 0x3A, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFE, 0x01
        }, _write(new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE)));
    }

    @Test
    public void testRoundTripExtremeScales() throws Exception
    {
        for (int scale : new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE + 1,
                -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE }) {
            BigDecimal input = new BigDecimal(BigInteger.valueOf(37), scale);
            assertEquals(input, _read(_write(input)), "Failed for scale "+scale);
        }
    }

    @Test
    public void testReadMaxExponent() throws Exception
    {
        // 1E+2147483648 (exponent +2^31 as uint32)
        assertEquals(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE),
                _read(new byte[] {
                        (byte) 0xC4, (byte) 0x82, 0x1A, (byte) 0x80, 0, 0, 0, 0x01 }));
        // and same as uint64
        assertEquals(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE),
                _read(new byte[] {
                        (byte) 0xC4, (byte) 0x82, 0x1B, 0, 0, 0, 0, (byte) 0x80, 0, 0, 0, 0x01 }));
    }

    @Test
    public void testReadOutOfRangeExponents() throws Exception
    {
        // 1E-2147483648: would need scale of 2^31
        _readFail(new byte[] {
                (byte) 0xC4, (byte) 0x82, 0x3A, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01 });
        // 1E+2147483649
        _readFail(new byte[] {
                (byte) 0xC4, (byte) 0x82, 0x1A, (byte) 0x80, 0, 0, 0x01, 0x01 });
    }

    private byte[] _write(BigDecimal value) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeNumber(value);
        }
        return bytes.toByteArray();
    }

    private BigDecimal _read(byte[] doc) throws Exception
    {
        try (JsonParser p = cborParser(doc)) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            BigDecimal result = p.getDecimalValue();
            assertNull(p.nextToken());
            return result;
        }
    }

    private void _readFail(byte[] doc) throws Exception
    {
        try (JsonParser p = cborParser(doc)) {
            p.nextToken();
            fail("Should not pass, got: "+p.getDecimalValue());
        } catch (StreamReadException e) {
            verifyException(e, "out of range for `BigDecimal`");
        }
    }
}
