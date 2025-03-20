package com.fasterxml.jackson.dataformat.ion;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.InputCoercionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;

// for [dataformats-ion#428]
public class IonNumberOverflowTest
{
    private final IonObjectMapper MAPPER = IonObjectMapper
            .builderForBinaryWriters()
            .build();

    // Test to ensure we handle value overflow (long->int) correctly
    @Test
    public void testIntCoercionOverflow() throws Exception {
        _testIntCoercionFail(Long.valueOf(3L * Integer.MAX_VALUE / 2L));
    }

    @Test
    public void testIntCoercionUnderflow() throws Exception {
        _testIntCoercionFail(Long.valueOf(3L * Integer.MIN_VALUE / 2L));
    }

    private void _testIntCoercionFail(Long input) throws Exception
    {
        final byte[] doc = MAPPER.writeValueAsBytes(input);

        // First, verify correct value decoding
        assertEquals(input, MAPPER.readValue(doc, Long.class));

        // And then over/underflow
        try {
            Integer result = MAPPER.readValue(doc, Integer.class);
            fail("Should not pass; got: "+result+" (from "+input+")");
        } catch (InputCoercionException e) {
            assertThat(e.getMessage(), Matchers.containsString("out of range of int"));
        }
    }

    // Test to ensure we handle value overflow (BigInteger->long) correctly
    @Test
    public void testLongCoercionOverflow() throws Exception {
        _testLongCoercionFail(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN));
    }

    @Test
    public void testLongCoercionUnderflow() throws Exception {
        _testLongCoercionFail(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.TEN));
    }

    private void _testLongCoercionFail(BigInteger input) throws Exception
    {
        final byte[] doc = MAPPER.writeValueAsBytes(input);

        // First, verify correct value decoding
        assertEquals(input, MAPPER.readValue(doc, BigInteger.class));

        // And then over/underflow
        try {
            Long result = MAPPER.readValue(doc, Long.class);
            fail("Should not pass; got: "+result+" (from "+input+")");
        } catch (InputCoercionException e) {
            assertThat(e.getMessage(), Matchers.containsString("out of range of long"));
        }
    }

    // [dataformats-binary#569]: incorrect overflow fail for long values (from BigInteger)
    @Test
    public void testLongAsBigIntegerSize() throws Exception {
        // Note: Values: Long.MAX_VALUE through Long.MAX_VALUE -7 are considered LONG by Ion.  
        BigInteger bigIntLongValue = new BigInteger(Long.MAX_VALUE + "").subtract(new BigInteger("10"));
        IonParser bigIntLongParser = (IonParser) new IonFactory().createParser(bigIntLongValue.toString());
        assertEquals(JsonToken.VALUE_NUMBER_INT, bigIntLongParser.nextToken());
        assertEquals(JsonParser.NumberType.BIG_INTEGER, bigIntLongParser.getNumberType());
        assertEquals(JsonParser.NumberTypeFP.UNKNOWN, bigIntLongParser.getNumberTypeFP());
        assertEquals(bigIntLongValue.longValue(), bigIntLongParser.getLongValue());
    }    
}
