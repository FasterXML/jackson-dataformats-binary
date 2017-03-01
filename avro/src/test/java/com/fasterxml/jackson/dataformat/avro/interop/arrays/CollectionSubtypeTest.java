package com.fasterxml.jackson.dataformat.avro.interop.arrays;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.junit.Assume;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.apacheDeserializer;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests collection subtypes such as {@link ArrayList}, {@link LinkedList}, {@link Stack}, {@link Set}, {@link HashSet}, {@link TreeSet},
 * {@link ConcurrentSkipListSet}, {@link CopyOnWriteArraySet}, and {@link CopyOnWriteArrayList} to ensure they are compatible with the
 * {@code ARRAY} schema type.
 */
public class CollectionSubtypeTest extends InteropTestBase {
    @Test
    public void testArrayList() throws IOException {
        ArrayList<String> original = new ArrayList<>();
        original.add("test");
        original.add("Second");
        //
        ArrayList<String> result = roundTrip(type(ArrayList.class, String.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testConcurrentSkipListSet() throws IOException {
        ConcurrentSkipListSet<Integer> original = new ConcurrentSkipListSet<>();
        original.add(1234);
        original.add(98768234);
        //
        ConcurrentSkipListSet<Integer> result = roundTrip(type(ConcurrentSkipListSet.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testCopyOnWriteArrayList() throws IOException{
        CopyOnWriteArrayList<Integer> original = new CopyOnWriteArrayList<>();
        original.add(1234);
        original.add(98768234);
        //
        CopyOnWriteArrayList<Integer> result = roundTrip(type(CopyOnWriteArrayList.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testCopyOnWriteArraySet() throws IOException{
        CopyOnWriteArraySet<Integer> original = new CopyOnWriteArraySet<>();
        original.add(1234);
        original.add(98768234);
        //
        CopyOnWriteArraySet<Integer> result = roundTrip(type(CopyOnWriteArraySet.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testEnumSet() throws IOException {
        // Bug in apache deserializer, can't handle EnumSet
        Assume.assumeTrue(deserializeFunctor != apacheDeserializer);
        EnumSet<DummyEnum> original = EnumSet.of(DummyEnum.EAST, DummyEnum.NORTH);
        //
        EnumSet<DummyEnum> result = roundTrip(type(EnumSet.class, DummyEnum.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testHashSet() throws IOException {
        HashSet<Integer> original = new HashSet<>();
        original.add(1234);
        original.add(98768234);
        //
        HashSet<Integer> result = roundTrip(type(HashSet.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testLinkedList() throws IOException {
        LinkedList<Integer> original = new LinkedList<>();
        original.add(1234);
        original.add(98768234);
        //
        LinkedList<Integer> result = roundTrip(type(LinkedList.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testList() throws IOException {
        List<String> original = new ArrayList<>();
        original.add("test");
        original.add("Second");
        //
        List<String> result = roundTrip(type(List.class, String.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testSet() throws IOException {
        // Bug in apache deserializer, can't handle Set
        Assume.assumeTrue(deserializeFunctor != apacheDeserializer);
        Set<Integer> original = new HashSet<>();
        original.add(1234);
        original.add(98768234);
        //
        Set<Integer> result = roundTrip(type(Set.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testStack() throws IOException {
        Stack<Integer> original = new Stack<>();
        original.add(1234);
        original.add(98768234);
        //
        Stack<Integer> result = roundTrip(type(Stack.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testTreeSet() throws IOException {
        TreeSet<Integer> original = new TreeSet<>();
        original.add(1234);
        original.add(98768234);
        //
        TreeSet<Integer> result = roundTrip(type(TreeSet.class, Integer.class), original);
        //
        assertThat(result).isEqualTo(original);
    }
}
