package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.avro.reflect.Nullable;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests records involving complex value types (Lists, Records, Maps, Enums)
 */
public class RecordWithComplexTest extends InteropTestBase
{
    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @RequiredArgsConstructor
    public static class RecursiveDummyRecord extends DummyRecord {
        @Nullable
        DummyRecord next;
        Map<String, Integer> simpleMap = new HashMap<>();
        Map<String, RecursiveDummyRecord> recursiveMap = new HashMap<>();
        List<Integer> requiredList = new ArrayList<>();
        @JsonProperty(required = true)
        private DummyEnum requiredEnum = DummyEnum.EAST;
        @Nullable
        private DummyEnum optionalEnum = null;

        public RecursiveDummyRecord(String firstValue, Integer secondValue, DummyRecord next) {
            super(firstValue, secondValue);
            this.next = next;
        }
    }

    @Before
    public void setup() {
        // 2.8 doesn't generate schemas with compatible namespaces for Apache deserializer
        assumeCompatibleNsForDeser();
        assumeCompatibleNsForSer();
    }

    @Test
    public void testEmptyRecordWithRecordValues() {
        Map<String, DummyRecord> original = new HashMap<>();
        //
        Map<String, DummyRecord> result = roundTrip(type(Map.class, String.class, DummyRecord.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testRecordWithListFields() {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.getRequiredList().add(9682584);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
        assertThat(result.getRequiredList()).isEqualTo(original.getRequiredList());
    }

    @Test
    public void testRecordWithMapFields() {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.getSimpleMap().put("Hello World", 9682584);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
        assertThat(result.getSimpleMap().get("Hello World")).isEqualTo(original.getSimpleMap().get("Hello World"));
    }

    @Test
    public void testRecordWithMissingRequiredEnumFields() {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.setRequiredEnum(null);
        //
        try {
            roundTrip(RecursiveDummyRecord.class, original);
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
    public void testRecordWithNullRequiredFields() {
        RecursiveDummyRecord original = new RecursiveDummyRecord(null, 12353, new DummyRecord("World", 234));
        //
        try {
            roundTrip(RecursiveDummyRecord.class, original);
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
    public void testRecordWithOptionalEnumField() {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.setOptionalEnum(DummyEnum.SOUTH);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testRecordWithRecordValues() {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
    }
}
