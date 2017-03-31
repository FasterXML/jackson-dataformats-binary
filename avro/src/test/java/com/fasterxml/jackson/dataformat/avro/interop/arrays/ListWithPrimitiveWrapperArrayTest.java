package com.fasterxml.jackson.dataformat.avro.interop.arrays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that lists behave as expected when primitive wrapper arrays (Byte[], Short[], Character[], Integer[], Long[], Float[], Double[])
 * are used as the value type
 */
public class ListWithPrimitiveWrapperArrayTest extends InteropTestBase {
    @Test
    public void testListWithBytes() throws IOException {
        List<Byte[]> original = new ArrayList<>();
        original.add(new Byte[]{(byte) 1});
        original.add(new Byte[0]);
        original.add(new Byte[]{(byte) -1});
        original.add(new Byte[]{Byte.MIN_VALUE});
        original.add(new Byte[]{Byte.MAX_VALUE});
        //
        List<Byte[]> result = roundTrip(type(List.class, Byte[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Byte[0][]));
    }

    @Test
    public void testListWithCharacters() throws IOException {
        List<Character[]> original = new ArrayList<>();
        original.add(new Character[]{(char) 1});
        original.add(new Character[0]);
        original.add(new Character[]{(char) -1});
        original.add(new Character[]{Character.MIN_VALUE});
        original.add(new Character[]{Character.MAX_VALUE});
        //
        List<Character[]> result = roundTrip(type(List.class, Character[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Character[0][]));
    }

    @Test
    public void testListWithDoubles() throws IOException {
        List<Double[]> original = new ArrayList<>();
        original.add(new Double[]{(double) 1});
        original.add(new Double[0]);
        original.add(new Double[]{(double) -1});
        original.add(new Double[]{Double.MIN_VALUE});
        original.add(new Double[]{Double.MAX_VALUE});
        //
        List<Double[]> result = roundTrip(type(List.class, Double[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Double[0][]));
    }

    @Test
    public void testListWithFloats() throws IOException {
        List<Float[]> original = new ArrayList<>();
        original.add(new Float[]{(float) 1});
        original.add(new Float[0]);
        original.add(new Float[]{(float) -1});
        original.add(new Float[]{Float.MIN_VALUE});
        original.add(new Float[]{Float.MAX_VALUE});
        //
        List<Float[]> result = roundTrip(type(List.class, Float[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Float[0][]));
    }

    @Test
    public void testListWithIntegers() throws IOException {
        List<Integer[]> original = new ArrayList<>();
        original.add(new Integer[]{(int) 1});
        original.add(new Integer[0]);
        original.add(new Integer[]{(int) -1});
        original.add(new Integer[]{Integer.MIN_VALUE});
        original.add(new Integer[]{Integer.MAX_VALUE});
        //
        List<Integer[]> result = roundTrip(type(List.class, Integer[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Integer[0][]));
    }

    @Test
    public void testListWithLongs() throws IOException {
        List<Long[]> original = new ArrayList<>();
        original.add(new Long[]{(long) 1});
        original.add(new Long[0]);
        original.add(new Long[]{(long) -1});
        original.add(new Long[]{Long.MIN_VALUE});
        original.add(new Long[]{Long.MAX_VALUE});
        //
        List<Long[]> result = roundTrip(type(List.class, Long[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Long[0][]));
    }

    @Test
    public void testListWithShorts() throws IOException {
        List<Short[]> original = new ArrayList<>();
        original.add(new Short[]{(short) 1});
        original.add(new Short[0]);
        original.add(new Short[]{(short) -1});
        original.add(new Short[]{Short.MIN_VALUE});
        original.add(new Short[]{Short.MAX_VALUE});
        //
        List<Short[]> result = roundTrip(type(List.class, Short[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new Short[0][]));
    }

    @Test
    public void testListWithStrings() throws IOException {
        List<String[]> original = new ArrayList<>();
        original.add(new String[]{"1"});
        original.add(new String[0]);
        original.add(new String[]{""});
        original.add(new String[]{"a really long string for testing"});
        original.add(new String[]{"multiple", "string", "values", "here"});
        //
        List<String[]> result = roundTrip(type(List.class, String[].class), original);
        //
        assertThat(result).containsExactly(original.toArray(new String[0][]));
    }
}
