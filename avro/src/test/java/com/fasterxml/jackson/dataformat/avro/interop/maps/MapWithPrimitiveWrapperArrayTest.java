package com.fasterxml.jackson.dataformat.avro.interop.maps;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that maps behave as expected when primitive wrapper arrays (Byte[], Short[], Character[], Integer[], Long[], Float[], Double[])
 * are used as the value type
 */
public class MapWithPrimitiveWrapperArrayTest extends InteropTestBase {
    @Test
    public void testMapWithBytes() throws IOException {
        Map<String, Byte[]> original = new HashMap<>();
        original.put("one", new Byte[]{(byte) 1});
        original.put("zero", new Byte[0]);
        original.put("negative one", new Byte[]{(byte) -1});
        original.put("min", new Byte[]{Byte.MIN_VALUE});
        original.put("max", new Byte[]{Byte.MAX_VALUE});
        //
        Map<String, Byte[]> result = roundTrip(type(Map.class, String.class, Byte[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithCharacters() throws IOException {
        Map<String, Character[]> original = new HashMap<>();
        original.put("one", new Character[]{(char) 1});
        original.put("zero", new Character[0]);
        original.put("negative one", new Character[]{(char) -1});
        original.put("min", new Character[]{Character.MIN_VALUE});
        original.put("max", new Character[]{Character.MAX_VALUE});
        //
        Map<String, Character[]> result = roundTrip(type(Map.class, String.class, Character[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithDoubles() throws IOException {
        Map<String, Double[]> original = new HashMap<>();
        original.put("one", new Double[]{(double) 1});
        original.put("zero", new Double[0]);
        original.put("negative one", new Double[]{(double) -1});
        original.put("min", new Double[]{Double.MIN_VALUE});
        original.put("max", new Double[]{Double.MAX_VALUE});
        //
        Map<String, Double[]> result = roundTrip(type(Map.class, String.class, Double[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithFloats() throws IOException {
        Map<String, Float[]> original = new HashMap<>();
        original.put("one", new Float[]{(float) 1});
        original.put("zero", new Float[0]);
        original.put("negative one", new Float[]{(float) -1});
        original.put("min", new Float[]{Float.MIN_VALUE});
        original.put("max", new Float[]{Float.MAX_VALUE});
        //
        Map<String, Float[]> result = roundTrip(type(Map.class, String.class, Float[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithIntegers() throws IOException {
        Map<String, Integer[]> original = new HashMap<>();
        original.put("one", new Integer[]{(int) 1});
        original.put("zero", new Integer[0]);
        original.put("negative one", new Integer[]{(int) -1});
        original.put("min", new Integer[]{Integer.MIN_VALUE});
        original.put("max", new Integer[]{Integer.MAX_VALUE});
        //
        Map<String, Integer[]> result = roundTrip(type(Map.class, String.class, Integer[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithLongs() throws IOException {
        Map<String, Long[]> original = new HashMap<>();
        original.put("one", new Long[]{(long) 1});
        original.put("zero", new Long[0]);
        original.put("negative one", new Long[]{(long) -1});
        original.put("min", new Long[]{Long.MIN_VALUE});
        original.put("max", new Long[]{Long.MAX_VALUE});
        //
        Map<String, Long[]> result = roundTrip(type(Map.class, String.class, Long[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithShorts() throws IOException {
        Map<String, Short[]> original = new HashMap<>();
        original.put("one", new Short[]{(short) 1});
        original.put("zero", new Short[0]);
        original.put("negative one", new Short[]{(short) -1});
        original.put("min", new Short[]{Short.MIN_VALUE});
        original.put("max", new Short[]{Short.MAX_VALUE});
        //
        Map<String, Short[]> result = roundTrip(type(Map.class, String.class, Short[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }

    @Test
    public void testMapWithStrings() throws IOException {
        Map<String, String[]> original = new HashMap<>();
        original.put("one", new String[]{"1"});
        original.put("zero", new String[0]);
        original.put("empty", new String[]{""});
        original.put("single", new String[]{"a really long string for testing"});
        original.put("multi", new String[]{"multiple", "string", "values", "here"});
        //
        Map<String, String[]> result = roundTrip(type(Map.class, String.class, String[].class), original);
        //
        assertThat(result).containsAllEntriesOf(original);
    }
}
