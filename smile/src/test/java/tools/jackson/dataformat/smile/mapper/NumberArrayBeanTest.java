package tools.jackson.dataformat.smile.mapper;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class NumberArrayBeanTest extends BaseTestForSmile
{
    static class IntsWrapper {
        public int[][] values;

        protected IntsWrapper() { }
        public IntsWrapper(int[][] v) { values = v; }
    }

    static class LongsWrapper {
        public long[][] values;

        protected LongsWrapper() { }
        public LongsWrapper(long[][] v) { values = v; }
    }

    static class DoublesWrapper {
        public double[][] values;

        protected DoublesWrapper() { }
        public DoublesWrapper(double[][] v) { values = v; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = smileMapper();

    @Test
    public void testIntArrayRoundTrip() throws Exception
    {
        int[][] inputArray = new int[][]{ { -5, 3 } };
        byte[] cbor = MAPPER.writeValueAsBytes(new IntsWrapper(inputArray));
        IntsWrapper result = MAPPER.readValue(cbor, IntsWrapper.class);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(1, result.values.length);
        assertEquals(2, result.values[0].length);
        assertEquals(inputArray[0][0], result.values[0][0]);
        assertEquals(inputArray[0][1], result.values[0][1]);
    }

    @Test
    public void testLongArrayRoundTrip() throws Exception
    {
        long[][] inputArray = new long[][]{ { 3L + Integer.MAX_VALUE, -3L + Integer.MIN_VALUE } };
        byte[] cbor = MAPPER.writeValueAsBytes(new LongsWrapper(inputArray));
        LongsWrapper result = MAPPER.readValue(cbor, LongsWrapper.class);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(1, result.values.length);
        assertEquals(2, result.values[0].length);
        assertEquals(inputArray[0][0], result.values[0][0]);
        assertEquals(inputArray[0][1], result.values[0][1]);
    }

    // for [dataformats-binary#31]
    @Test
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
