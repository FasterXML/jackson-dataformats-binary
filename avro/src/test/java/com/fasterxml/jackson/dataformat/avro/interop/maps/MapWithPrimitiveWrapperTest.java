package com.fasterxml.jackson.dataformat.avro.interop.maps;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that maps behave as expected when primitive wrappers (Byte, Short, Character, Integer, Long, Float, Double) are used as the value
 * type
 */
public class MapWithPrimitiveWrapperTest extends InteropTestBase {
    @Test
    public void testMapWithBytes() throws IOException {
        Map<String, Byte> original = new HashMap<>();
        original.put("one", (byte) 1);
        original.put("zero", (byte) 0);
        original.put("negative one", (byte) -1);
        original.put("min", Byte.MIN_VALUE);
        original.put("max", Byte.MAX_VALUE);
        //
        Map<String, Byte> result = roundTrip(type(Map.class, String.class, Byte.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithCharacters() throws IOException {
        Map<String, Character> original = new HashMap<>();
        original.put("one", (char) 1);
        original.put("zero", (char) 0);
        original.put("negative one", (char) -1);
        original.put("min", Character.MIN_VALUE);
        original.put("max", Character.MAX_VALUE);
        //
        Map<String, Character> result = roundTrip(type(Map.class, String.class, Character.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithDoubles() throws IOException {
        Map<String, Double> original = new HashMap<>();
        original.put("one", 1D);
        original.put("zero", 0D);
        original.put("negative one", -1D);
        original.put("min", Double.MIN_VALUE);
        original.put("max", Double.MAX_VALUE);
        //
        Map<String, Double> result = roundTrip(type(Map.class, String.class, Double.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithFloats() throws IOException {
        Map<String, Float> original = new HashMap<>();
        original.put("one", 1F);
        original.put("zero", 0F);
        original.put("negative one", -1F);
        original.put("min", Float.MIN_VALUE);
        original.put("max", Float.MAX_VALUE);
        //
        Map<String, Float> result = roundTrip(type(Map.class, String.class, Float.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithIntegers() throws IOException {
        Map<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("zero", 0);
        original.put("negative one", -1);
        original.put("min", Integer.MIN_VALUE);
        original.put("max", Integer.MAX_VALUE);
        //
        Map<String, Integer> result = roundTrip(type(Map.class, String.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithLongs() throws IOException {
        Map<String, Long> original = new HashMap<>();
        original.put("one", 1L);
        original.put("zero", 0L);
        original.put("negative one", -1L);
        original.put("min", Long.MIN_VALUE);
        original.put("max", Long.MAX_VALUE);
        //
        Map<String, Long> result = roundTrip(type(Map.class, String.class, Long.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithShorts() throws IOException {
        Map<String, Short> original = new HashMap<>();
        original.put("one", (short) 1);
        original.put("zero", (short) 0);
        original.put("negative one", (short) -1);
        original.put("min", Short.MIN_VALUE);
        original.put("max", Short.MAX_VALUE);
        //
        Map<String, Short> result = roundTrip(type(Map.class, String.class, Short.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testMapWithStrings() throws IOException {
        Map<String, String> original = new HashMap<>();
        original.put("one", "1");
        original.put("empty", "");
        original.put("single", "a really long string for testing");
        //
        Map<String, String> result = roundTrip(type(Map.class, String.class, String.class), original);
        //
        assertThat(result).isEqualTo(original);
    }
}
