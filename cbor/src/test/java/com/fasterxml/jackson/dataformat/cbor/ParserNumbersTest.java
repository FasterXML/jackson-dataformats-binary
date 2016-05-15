package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser.NumberType;

public class ParserNumbersTest extends CBORTestBase
{
    public void testIntValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyInt(f, 13);
        _verifyInt(f, -19);
        // two bytes
        _verifyInt(f, 255);
        _verifyInt(f, -127);
        // three
        _verifyInt(f, 256);
        _verifyInt(f, 0xFFFF);
        _verifyInt(f, -300);
        _verifyInt(f, -0xFFFF);
        // and all 4 bytes
        _verifyInt(f, 0x7FFFFFFF);
        _verifyInt(f, 0x70000000 << 1);
    }

    private void _verifyInt(CBORFactory f, int value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(value, p.getIntValue());
        assertEquals((double) value, p.getDoubleValue());
        assertEquals(BigInteger.valueOf(value), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(value), p.getDecimalValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testLongValues() throws Exception
    {
        CBORFactory f = cborFactory();
        _verifyLong(f, 1L + Integer.MAX_VALUE);
        _verifyLong(f, Long.MIN_VALUE);
        _verifyLong(f, Long.MAX_VALUE);
        _verifyLong(f, -1L + Integer.MIN_VALUE);
    }

    private void _verifyLong(CBORFactory f, long value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(value, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals((double) value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }
    
    public void testFloatValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyFloat(f, 0.25);
        _verifyFloat(f, 20.5);

        // But then, oddity: 16-bit mini-float
        // Examples from [https://en.wikipedia.org/wiki/Half_precision_floating-point_format]
        _verifyHalfFloat(f, 0, 0.0);
        _verifyHalfFloat(f, 0x3C00, 1.0);
        _verifyHalfFloat(f, 0xC000, -2.0);
        _verifyHalfFloat(f, 0x7BFF, 65504.0);
        _verifyHalfFloat(f, 0x7C00, Double.POSITIVE_INFINITY);
        _verifyHalfFloat(f, 0xFC00, Double.NEGATIVE_INFINITY);

        // ... can add more, but need bit looser comparison if so
    }

    private void _verifyFloat(CBORFactory f, double value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber((float) value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        if (NumberType.FLOAT != p.getNumberType()) {
            fail("Expected `NumberType.FLOAT`, got "+p.getNumberType()+": "+p.getText());
        }
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    private void _verifyHalfFloat(JsonFactory f, int i16, double value) throws IOException
    {
        JsonParser p = f.createParser(new byte[] {
                (byte) (CBORConstants.PREFIX_TYPE_MISC + 25),
                (byte) (i16 >> 8), (byte) i16
        });
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testFloatNumberType() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeStartObject();
        generator.writeFieldName("foo");
        generator.writeNumber(3f);
        generator.writeEndObject();
        generator.close();

        CBORParser parser = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(NumberType.FLOAT, parser.getNumberType()); // fails with expected <FLOAT> but was <DOUBLE>
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

}
