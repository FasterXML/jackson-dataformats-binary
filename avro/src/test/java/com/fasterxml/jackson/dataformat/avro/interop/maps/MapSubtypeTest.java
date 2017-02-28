package com.fasterxml.jackson.dataformat.avro.interop.maps;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.apacheDeserializer;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.getApacheSchema;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests map subtypes such as {@link TreeMap}, {@link ConcurrentHashMap}, and {@link ConcurrentSkipListMap}. The Apache Avro implementation
 * has a bug that will cause these to fail with ClassCastExceptions since it assumes all maps are {@link HashMap HashMaps}.
 */
public class MapSubtypeTest extends InteropTestBase {
    @Before
    public void ignoreApacheMapSubtypeBug() {
        // The Apache Avro implementation has a bug that causes all of these tests to fail. Conditionally ignore these tests when running
        // with Apache deserializer implementation
        Assume.assumeTrue(deserializeFunctor != apacheDeserializer);
    }

    @Test
    public void testHashMap() {
        HashMap<String, Integer> original = new HashMap<>();
        original.put("test", 1234);
        original.put("Second", 98768234);
        //
        HashMap<String, Integer> result = roundTrip(type(HashMap.class, String.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testConcurrentHashMap() {
        ConcurrentHashMap<String, Integer> original = new ConcurrentHashMap<>();
        original.put("test", 1234);
        original.put("Second", 98768234);
        //
        ConcurrentHashMap<String, Integer> result = roundTrip(type(ConcurrentHashMap.class, String.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testConcurrentSkipListMap() {
        ConcurrentSkipListMap<String, Integer> original = new ConcurrentSkipListMap<>();
        original.put("test", 1234);
        original.put("Second", 98768234);
        //
        ConcurrentSkipListMap<String, Integer> result = roundTrip(type(ConcurrentSkipListMap.class, String.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testTreeMap() {
        TreeMap<String, Integer> original = new TreeMap<>();
        original.put("test", 1234);
        original.put("Second", 98768234);
        //
        TreeMap<String, Integer> result = roundTrip(type(TreeMap.class, String.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testEnumMap() {
        // Apache schema generator can't handle EnumMaps
        Assume.assumeTrue(schemaFunctor != getApacheSchema);

        EnumMap<DummyEnum, Integer> original = new EnumMap<>(DummyEnum.class);
        original.put(DummyEnum.NORTH, 1234);
        original.put(DummyEnum.SOUTH, 98768234);
        //
        EnumMap<DummyEnum, Integer> result = roundTrip(type(EnumMap.class, DummyEnum.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }
}
