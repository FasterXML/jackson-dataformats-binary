package com.fasterxml.jackson.dataformat.cbor.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class NumberBeanTest extends CBORTestBase
{
    static class IntBean {
        public int value;

        public IntBean(int v) { value = v; }
        protected IntBean() { }
    }

    static class LongBean {
        public long value;

        public LongBean(long v) { value = v; }
        protected LongBean() { }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = cborMapper();

    public void testIntRoundTrip() throws Exception
    {
        for (int i : new int[] { 0, 1, -1,
                99, -120,
                5500, -9000,
                Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            IntBean input = new IntBean(i);
            byte[] b = MAPPER.writeValueAsBytes(input);
            IntBean result = MAPPER.readValue(b, IntBean.class);
            assertEquals(input.value, result.value);
        }
    }

    public void testLongRoundTrip() throws Exception
    {
        for (long v : new long[] { 0, 1, -1,
                100L, -200L,
                5000L, -3600L,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                1L + Integer.MAX_VALUE, -1L + Integer.MIN_VALUE,
                2330462449L, // from [dataformats-binary#30]
                Long.MIN_VALUE, Long.MAX_VALUE
                }) {
            LongBean input = new LongBean(v);
            byte[] b = MAPPER.writeValueAsBytes(input);
            LongBean result = MAPPER.readValue(b, LongBean.class);
            assertEquals(input.value, result.value);
        }
    }

    // for [dataformats-binary#32] coercion of Float into Double
    public void testUntypedWithFloat() throws Exception
    {
        Object[] input = new Object[] { Float.valueOf(0.5f) };
        byte[] b = MAPPER.writeValueAsBytes(input);
        Object[] result = MAPPER.readValue(b, Object[].class);
        assertEquals(1, result.length);
        assertEquals(Float.class, result[0].getClass());
        assertEquals(input[0], result[0]);
    }
}
