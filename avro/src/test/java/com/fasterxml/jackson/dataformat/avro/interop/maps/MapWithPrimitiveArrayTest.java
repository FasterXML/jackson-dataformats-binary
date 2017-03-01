package com.fasterxml.jackson.dataformat.avro.interop.maps;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that maps behave as expected when primitive arrays (byte[], short[], char[], int[], long[], float[], double[]) are used as the
 * value type
 */
public class MapWithPrimitiveArrayTest extends InteropTestBase {
    @Test
    public void testMapWithBytes() throws IOException {
        Map<String, byte[]> original = new HashMap<>();
        original.put("one", new byte[]{(byte)1 });
        original.put("zero", new byte[0]);
        original.put("negative one", new byte[]{(byte) -1});
        original.put("min", new byte[]{Byte.MIN_VALUE});
        original.put("max", new byte[]{Byte.MAX_VALUE});
        //
        Map<String, byte[]> result = roundTrip(type(Map.class, String.class, byte[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithCharacters() throws IOException {
        Map<String, char[]> original = new HashMap<>();
        original.put("one", new char[]{(char) 1});
        original.put("zero", new char[0]);
        original.put("negative one", new char[]{(char) -1});
        original.put("min", new char[]{Character.MIN_VALUE});
        original.put("max", new char[]{Character.MAX_VALUE});
        //
        Map<String, char[]> result = roundTrip(type(Map.class, String.class, char[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithDoubles() throws IOException {
        Map<String, double[]> original = new HashMap<>();
        original.put("one", new double[]{(double) 1});
        original.put("zero", new double[0]);
        original.put("negative one", new double[]{(double) -1});
        original.put("min", new double[]{Double.MIN_VALUE});
        original.put("max", new double[]{Double.MAX_VALUE});
        //
        Map<String, double[]> result = roundTrip(type(Map.class, String.class, double[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithFloats() throws IOException {
        Map<String, float[]> original = new HashMap<>();
        original.put("one", new float[]{(float) 1});
        original.put("zero", new float[0]);
        original.put("negative one", new float[]{(float) -1});
        original.put("min", new float[]{Float.MIN_VALUE});
        original.put("max", new float[]{Float.MAX_VALUE});
        //
        Map<String, float[]> result = roundTrip(type(Map.class, String.class, float[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithIntegers() throws IOException {
        Map<String, int[]> original = new HashMap<>();
        original.put("one", new int[]{(int) 1});
        original.put("zero", new int[0]);
        original.put("negative one", new int[]{(int) -1});
        original.put("min", new int[]{Integer.MIN_VALUE});
        original.put("max", new int[]{Integer.MAX_VALUE});
        //
        Map<String, int[]> result = roundTrip(type(Map.class, String.class, int[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithLongs() throws IOException {
        Map<String, long[]> original = new HashMap<>();
        original.put("one", new long[]{(long) 1});
        original.put("zero", new long[0]);
        original.put("negative one", new long[]{(long) -1});
        original.put("min", new long[]{Long.MIN_VALUE});
        original.put("max", new long[]{Long.MAX_VALUE});
        //
        Map<String, long[]> result = roundTrip(type(Map.class, String.class, long[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithShorts() throws IOException {
        Map<String, short[]> original = new HashMap<>();
        original.put("one", new short[]{(short) 1});
        original.put("zero", new short[0]);
        original.put("negative one", new short[]{(short) -1});
        original.put("min", new short[]{Short.MIN_VALUE});
        original.put("max", new short[]{Short.MAX_VALUE});
        //
        Map<String, short[]> result = roundTrip(type(Map.class, String.class, short[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }
}
