package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

// tests for [cbor#17]
public class BigNumbersTest extends CBORTestBase
{
    public void testBigDecimal() throws Exception
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

        // ensure mantissa is beyond long; more than 22 digits or so
        bd = new BigDecimal("1234567890.12345678901234567890");
        _testBigDecimal(bd);
        _testBigDecimal(bd.negate());
    }

    private void _testBigDecimal(BigDecimal expValue) throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeStartObject();
        sourceGen.writeFieldName("a");
        sourceGen.writeNumber(expValue);
        sourceGen.writeEndObject();
        sourceGen.close();

        byte[] b = sourceBytes.toByteArray();
        
        // but verify that the original content can be parsed
        CBORParser parser = cborParser(b);
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("a", parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(expValue, parser.getDecimalValue());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

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
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(expValue);
        sourceGen.close();

        // but verify that the original content can be parsed
        CBORParser parser = cborParser(sourceBytes.toByteArray());
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
        parser.close();

    }
}
