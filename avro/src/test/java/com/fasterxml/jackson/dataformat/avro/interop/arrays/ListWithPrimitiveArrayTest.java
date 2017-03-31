package com.fasterxml.jackson.dataformat.avro.interop.arrays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that maps behave as expected when primitive arrays (byte[], short[], char[], int[], long[], float[], double[]) are used as the
 * value type
 */
public class ListWithPrimitiveArrayTest extends InteropTestBase {
    @Test
    public void testListWithBytes() throws IOException {
        List<byte[]> original = new ArrayList<>();
        original.add(new byte[]{(byte) 1});
        original.add(new byte[0]);
        original.add(new byte[]{(byte) -1});
        original.add(new byte[]{Byte.MIN_VALUE});
        original.add(new byte[]{Byte.MAX_VALUE});
        //
        List<byte[]> result = roundTrip(type(List.class, byte[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new byte[0][]));
    }

    @Test
    public void testListWithCharacters() throws IOException {
        List<char[]> original = new ArrayList<>();
        original.add(new char[]{(char) 1});
        original.add(new char[0]);
        original.add(new char[]{(char) -1});
        original.add(new char[]{Character.MIN_VALUE});
        original.add(new char[]{Character.MAX_VALUE});
        //
        List<char[]> result = roundTrip(type(List.class, char[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new char[0][]));
    }

    @Test
    public void testListWithDoubles() throws IOException {
        List<double[]> original = new ArrayList<>();
        original.add(new double[]{(double) 1});
        original.add(new double[0]);
        original.add(new double[]{(double) -1});
        original.add(new double[]{Double.MIN_VALUE});
        original.add(new double[]{Double.MAX_VALUE});
        //
        List<double[]> result = roundTrip(type(List.class, double[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new double[0][]));
    }

    @Test
    public void testListWithFloats() throws IOException {
        List<float[]> original = new ArrayList<>();
        original.add(new float[]{(float) 1});
        original.add(new float[0]);
        original.add(new float[]{(float) -1});
        original.add(new float[]{Float.MIN_VALUE});
        original.add(new float[]{Float.MAX_VALUE});
        //
        List<float[]> result = roundTrip(type(List.class, float[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new float[0][]));
    }

    @Test
    public void testListWithIntegers() throws IOException {
        List<int[]> original = new ArrayList<>();
        original.add(new int[]{(int) 1});
        original.add(new int[0]);
        original.add(new int[]{(int) -1});
        original.add(new int[]{Integer.MIN_VALUE});
        original.add(new int[]{Integer.MAX_VALUE});
        //
        List<int[]> result = roundTrip(type(List.class, int[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new int[0][]));
    }

    @Test
    public void testListWithLongs() throws IOException {
        List<long[]> original = new ArrayList<>();
        original.add(new long[]{(long) 1});
        original.add(new long[0]);
        original.add(new long[]{(long) -1});
        original.add(new long[]{Long.MIN_VALUE});
        original.add(new long[]{Long.MAX_VALUE});
        //
        List<long[]> result = roundTrip(type(List.class, long[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new long[0][]));
    }

    @Test
    public void testListWithShorts() throws IOException {
        List<short[]> original = new ArrayList<>();
        original.add(new short[]{(short) 1});
        original.add(new short[0]);
        original.add(new short[]{(short) -1});
        original.add(new short[]{Short.MIN_VALUE});
        original.add(new short[]{Short.MAX_VALUE});
        //
        List<short[]> result = roundTrip(type(List.class, short[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new short[0][]));
    }
}
