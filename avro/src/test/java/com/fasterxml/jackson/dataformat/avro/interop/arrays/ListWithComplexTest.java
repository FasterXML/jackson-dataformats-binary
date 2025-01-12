package com.fasterxml.jackson.dataformat.avro.interop.arrays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.DummyRecord;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests lists involving complex element types (Lists, Records, Maps, Enums)
 */
public class ListWithComplexTest extends InteropTestBase
{

    @Test
    public void testEmptyListWithRecordElements() throws IOException {
        List<DummyRecord> original = new ArrayList<>();
        //
        List<DummyRecord> result = roundTrip(type(List.class, DummyRecord.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithEnumElements() throws IOException {
        List<DummyEnum> original = new ArrayList<>();
        original.add(DummyEnum.EAST);
        original.add(DummyEnum.WEST);
        //
        List<DummyEnum> result = roundTrip(type(List.class, DummyEnum.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithListElements() throws IOException {
        List<List<List<String>>> original = new ArrayList<>();
        original.add(new ArrayList<List<String>>());
        original.get(0).add(Collections.singletonList("Hello"));
        original.add(new ArrayList<List<String>>());
        original.get(1).add(Collections.singletonList("World"));
        //
        List<List<List<String>>> result = roundTrip(type(List.class, type(List.class, type(List.class, String.class))), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithMapElements() throws IOException {
        List<Map<String, Integer>> original = new ArrayList<>();
        original.add(Collections.singletonMap("Hello", 1));
        original.add(Collections.singletonMap("World", 2));
        //
        List<Map<String, Integer>> result = roundTrip(type(List.class, type(Map.class, String.class, Integer.class)), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testListWithNullElements() {
        List<DummyRecord> original = new ArrayList<>();
        original.add(null);
        //
        try {
            roundTrip(type(List.class, DummyRecord.class), original);
            fail("Should throw an NPE");
        } catch (Throwable e) {
            // Avro NullPointerException
            // Jackson RuntimeException -> JsonMappingException -> NullPointerException
            while (e.getCause() != null && e.getCause() != e) {
                e = e.getCause();
            }
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    public void testListWithRecordElements() throws IOException {
        List<DummyRecord> original = new ArrayList<>();
        original.add(new DummyRecord("test", 2));
        original.add(new DummyRecord("test 2", 1235));
        original.add(new DummyRecord("test 3", -234));
        //
        List<DummyRecord> result = roundTrip(type(List.class, DummyRecord.class), original);
        //
        assertThat(result).isEqualTo(original);
    }
}
