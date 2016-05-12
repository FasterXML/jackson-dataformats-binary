package com.fasterxml.jackson.dataformat.cbor.failing;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.dataformat.cbor.*;

// test for [cbor#17]
public class BigDecimalTest extends CBORTestBase
{
    public void testBigDecimal() throws Exception
    {
        final ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        final CBORGenerator sourceGen = cborGenerator(sourceBytes);
        sourceGen.writeNumber(BigDecimal.ONE);
        sourceGen.close();

        // but verify that the origina content can be parsed
        CBORParser parser = cborParser(sourceBytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(BigDecimal.ONE, parser.getDecimalValue());
        parser.close();
    }
}
