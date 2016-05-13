package com.fasterxml.jackson.dataformat.cbor.failing;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.*;

// tests for [cbor#17]
public class BigNumbersTest extends CBORTestBase
{
    public void testBigDecimal() throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(BigDecimal.ONE);
        sourceGen.close();

        // but verify that the original content can be parsed
        CBORParser parser = cborParser(sourceBytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(BigDecimal.ONE, parser.getDecimalValue());
        parser.close();
    }

    public void testBigInteger() throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(BigInteger.TEN);
        sourceGen.close();

        // but verify that the original content can be parsed
        CBORParser parser = cborParser(sourceBytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(BigInteger.TEN, parser.getBigIntegerValue());
        parser.close();
    }
}
