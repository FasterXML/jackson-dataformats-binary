package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.AvroTypeException;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.interop.DummyRecord;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests records involving complex value types (Lists, Records, Maps, Enums)
 */
public class RecordWithComplexTest extends InteropTestBase
{

    @Test
    public void testEmptyRecordWithRecordValues() throws IOException {
        Map<String, DummyRecord> original = new HashMap<>();
        //
        Map<String, DummyRecord> result = roundTrip(type(Map.class, String.class, DummyRecord.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testRecordWithListFields() throws IOException {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.requiredList.add(9682584);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
        assertThat(result.requiredList).isEqualTo(original.requiredList);
    }

    @Test
    public void testRecordWithMapFields() throws IOException {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.simpleMap.put("Hello World", 9682584);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
        assertThat(result.simpleMap.get("Hello World")).isEqualTo(original.simpleMap.get("Hello World"));
    }

    @Test
    public void testRecordWithMissingRequiredEnumFields() {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.requiredEnum = null;
        //
        try {
            roundTrip(RecursiveDummyRecord.class, original);
            fail("Should throw an exception");
        } catch (IOException e) { // sometimes we get this
            assertThat(e).isInstanceOf(JsonMappingException.class);
        } catch (AvroTypeException e) { // sometimes (not wrapped)
            ;
        }
    }

    @Test
    public void testRecordWithNullRequiredFields() throws IOException {
        RecursiveDummyRecord original = new RecursiveDummyRecord(null, 12353, new DummyRecord("World", 234));
        //
        try {
            roundTrip(RecursiveDummyRecord.class, original);
            fail("Should throw an exception");
        } catch (JsonMappingException e) {
            // 28-Feb-2017, tatu: Sometimes we get this (probably when using ObjectWriter)
        } catch (NullPointerException e) {
            // 28-Feb-2017, tatu: Should NOT just pass NPE, but for now nothing much we can do
        }
    }

    @Test
    public void testRecordWithOptionalEnumField() throws IOException {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.optionalEnum = DummyEnum.SOUTH;
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testRecordWithRecordValues() throws IOException {
        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
    }
}
