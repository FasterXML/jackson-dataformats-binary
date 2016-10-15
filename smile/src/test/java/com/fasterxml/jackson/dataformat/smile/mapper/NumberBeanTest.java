package com.fasterxml.jackson.dataformat.smile.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class NumberBeanTest extends BaseTestForSmile
{
    static class DoublesWrapper {
        public double[][] values;

        protected DoublesWrapper() { }
        public DoublesWrapper(double[][] v) { values = v; }
    }

    private final ObjectMapper MAPPER = smileMapper();

    // for [dataformats-binary#31]
    public void testDoubleArrayRoundTrip() throws Exception
    {
        double[][] inputArray = new double[][]{ { 0.25, -1.5 } };
        byte[] cbor = MAPPER.writeValueAsBytes(new DoublesWrapper(inputArray));
        DoublesWrapper result = MAPPER.readValue(cbor, DoublesWrapper.class);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(1, result.values.length);
        assertEquals(2, result.values[0].length);
        assertEquals(inputArray[0][0], result.values[0][0]);
        assertEquals(inputArray[0][1], result.values[0][1]);
    }
}
