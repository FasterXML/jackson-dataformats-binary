package com.fasterxml.jackson.dataformat.avro.interop.arrays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Lists behave as expected when primitive wrappers (Byte, Short, Character, Integer, Long, Float, Double) are used as the value
 * type
 */
public class ListWithPrimitiveWrapperTest extends InteropTestBase {
    @Test
    public void testListWithBytes() throws IOException {
        List<Byte> original = new ArrayList<>();
        original.add((byte) 1);
        original.add((byte) 0);
        original.add((byte) -1);
        original.add(Byte.MIN_VALUE);
        original.add(Byte.MAX_VALUE);
        //
        List<Byte> result = roundTrip(type(List.class, Byte.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithCharacters() throws IOException {
        List<Character> original = new ArrayList<>();
        original.add((char) 1);
        original.add((char) 0);
        original.add((char) -1);
        original.add(Character.MIN_VALUE);
        original.add(Character.MAX_VALUE);
        //
        List<Character> result = roundTrip(type(List.class, Character.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithDoubles() throws IOException {
        List<Double> original = new ArrayList<>();
        original.add(1D);
        original.add(0D);
        original.add(-1D);
        original.add(Double.MIN_VALUE);
        original.add(Double.MAX_VALUE);
        //
        List<Double> result = roundTrip(type(List.class, Double.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithFloats() throws IOException {
        List<Float> original = new ArrayList<>();
        original.add(1F);
        original.add(0F);
        original.add(-1F);
        original.add(Float.MIN_VALUE);
        original.add(Float.MAX_VALUE);
        //
        List<Float> result = roundTrip(type(List.class, Float.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithIntegers() throws IOException {
        List<Integer> original = new ArrayList<>();
        original.add(1);
        original.add(0);
        original.add(-1);
        original.add(Integer.MIN_VALUE);
        original.add(Integer.MAX_VALUE);
        //
        List<Integer> result = roundTrip(type(List.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithLongs() throws IOException {
        List<Long> original = new ArrayList<>();
        original.add(1L);
        original.add(0L);
        original.add(-1L);
        original.add(Long.MIN_VALUE);
        original.add(Long.MAX_VALUE);
        //
        List<Long> result = roundTrip(type(List.class, Long.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithShorts() throws IOException {
        List<Short> original = new ArrayList<>();
        original.add((short) 1);
        original.add((short) 0);
        original.add((short) -1);
        original.add(Short.MIN_VALUE);
        original.add(Short.MAX_VALUE);
        //
        List<Short> result = roundTrip(type(List.class, Short.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithStrings() throws IOException {
        List<String> original = new ArrayList<>();
        original.add("1");
        original.add("");
        original.add("a really long string for testing");
        //
        List<String> result = roundTrip(type(List.class, String.class), original);
        //
        assertThat(result).isEqualTo(original);
    }
}
