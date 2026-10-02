package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CBORBigNumberParserTest extends CBORTestBase
{
    @Test
    public void testBigDecimalShort() throws Exception
    {
        _testBigDecimal(BigDecimal.ONE);
        _testBigDecimal(BigDecimal.ZERO);
        _testBigDecimal(BigDecimal.TEN);
        _testBigDecimal(BigDecimal.ONE.scaleByPowerOfTen(-1));
        _testBigDecimal(BigDecimal.ONE.scaleByPowerOfTen(-3));
        _testBigDecimal(BigDecimal.ONE.scaleByPowerOfTen(-100));
        _testBigDecimal(BigDecimal.ONE.scaleByPowerOfTen(3));
        _testBigDecimal(BigDecimal.ONE.scaleByPowerOfTen(137));

        _testBigDecimal(new BigDecimal("0.01"));
        _testBigDecimal(new BigDecimal("0.33"));
        _testBigDecimal(new BigDecimal("1.1"));
        _testBigDecimal(new BigDecimal("900.373"));

        BigDecimal bd = new BigDecimal("12345.667899024");
        _testBigDecimal(bd);
        _testBigDecimal(bd.negate());
    }

    @Test
    public void testBigDecimalLonger() throws Exception
    {
        // ensure mantissa is beyond long; more than 22 digits or so
        BigDecimal bd = new BigDecimal("1234567890.12345678901234567890");
        _testBigDecimal(bd);
        _testBigDecimal(bd.negate());
    }

    private void _testBigDecimal(BigDecimal expValue) throws Exception
    {
        _testBigDecimalInArray(expValue);
        _testBigDecimalInObject(expValue);
    }

    private void _testBigDecimalInArray(BigDecimal expValue) throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        try (final CBORGenerator sourceGen = cborGenerator(sourceBytes)) {
            sourceGen.writeStartArray();
            sourceGen.writeNumber(expValue);
            sourceGen.writeEndArray();
        }

        byte[] b = sourceBytes.toByteArray();

        // but verify that the original content can be parsed
        try (CBORParser parser = cborParser(b)) {
            assertToken(JsonToken.START_ARRAY, parser.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(expValue, parser.getDecimalValue());
            assertToken(JsonToken.END_ARRAY, parser.nextToken());
        }
    }

    private void _testBigDecimalInObject(BigDecimal expValue) throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        try (final CBORGenerator sourceGen = cborGenerator(sourceBytes)) {
            sourceGen.writeStartObject();
            sourceGen.writeName("a");
            sourceGen.writeNumber(expValue);
            sourceGen.writeEndObject();
        }

        byte[] b = sourceBytes.toByteArray();

        // but verify that the original content can be parsed
        try (CBORParser parser = cborParser(b)) {
            assertToken(JsonToken.START_OBJECT, parser.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
            assertEquals("a", parser.currentName());
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(expValue, parser.getDecimalValue());
            assertToken(JsonToken.END_OBJECT, parser.nextToken());
        }
    }

    @Test
    public void testBigInteger() throws Exception
    {
        _testBigInteger(BigInteger.TEN);
        _testBigInteger(BigInteger.TEN.negate());
        _testBigInteger(BigInteger.valueOf(Integer.MAX_VALUE));
        _testBigInteger(BigInteger.valueOf(Integer.MIN_VALUE));
        _testBigInteger(BigInteger.valueOf(Long.MAX_VALUE));
        _testBigInteger(BigInteger.valueOf(Long.MIN_VALUE));
    }

    private void _testBigInteger(BigInteger expValue) throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        try (CBORGenerator sourceGen = cborGenerator(sourceBytes)) {
            sourceGen.writeNumber(expValue);
            sourceGen.close();
        }

        // but verify that the original content can be parsed
        try (CBORParser parser = cborParser(sourceBytes.toByteArray())) {
            assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(expValue, parser.getBigIntegerValue());
    
            // also, coercion to long at least
            long expL = expValue.longValue();
            assertEquals(expL, parser.getLongValue());
    
            // and int, if feasible
            if (expL >= Integer.MIN_VALUE && expL <= Integer.MAX_VALUE) {
                assertEquals((int) expL, parser.getIntValue());
            }
    
            assertNull(parser.nextToken());
        }
    }

    // Bignum content is unsigned: high bit of first byte must not make value negative
    @Test
    public void testBigIntegerHighBitSet() throws Exception
    {
        // 2([0x80 00 00 00]) == 2^31
        _verifyBigInteger(BigInteger.ONE.shiftLeft(31),
                new byte[] { (byte) 0xC2, 0x44, (byte) 0x80, 0, 0, 0 });
        // 2([0xFF x 9]) == 2^72 - 1
        final byte FF = (byte) 0xFF;
        _verifyBigInteger(BigInteger.ONE.shiftLeft(72).subtract(BigInteger.ONE),
                new byte[] { (byte) 0xC2, 0x49, FF, FF, FF, FF, FF, FF, FF, FF, FF });
        // 3([0x80 00 00 00]) == -1 - 2^31 with standard encoding
        _verifyBigInteger(BigInteger.ONE.shiftLeft(31).add(BigInteger.ONE).negate(),
                cborFactoryBuilder().enable(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING).build(),
                new byte[] { (byte) 0xC3, 0x44, (byte) 0x80, 0, 0, 0 });
        // (legacy negative decoding left as-is for compatibility: see `CBORMapperTest`)
    }

    private void _verifyBigInteger(BigInteger exp, byte[] doc) throws Exception {
        _verifyBigInteger(exp, cborFactory(), doc);
    }

    private void _verifyBigInteger(BigInteger exp, CBORFactory f, byte[] doc) throws Exception
    {
        try (CBORParser p = cborParser(f, doc)) {
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(exp, p.getBigIntegerValue());
            assertNull(p.nextToken());
        }
    }
}
