package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.interop.DummyRecord;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests records involving complex value types (Lists, Records, Maps, Enums)
 */
public class RecordWithComplexTest extends InteropTestBase
{
    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testEmptyRecordWithRecordValues(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        Map<String, DummyRecord> original = new HashMap<>();
        //
        Map<String, DummyRecord> result = roundTrip(type(Map.class, String.class, DummyRecord.class), original);
        //
        assertThat(result).isEqualTo(original);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testRecordWithListFields(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.requiredList.add(9682584);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
        assertThat(result.requiredList).isEqualTo(original.requiredList);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testRecordWithMapFields(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.simpleMap.put("Hello World", 9682584);
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
        assertThat(result.simpleMap.get("Hello World")).isEqualTo(original.simpleMap.get("Hello World"));
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testRecordWithMissingRequiredEnumFields(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

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

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testRecordWithNullRequiredFields(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

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

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testRecordWithOptionalEnumField(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        original.optionalEnum = DummyEnum.SOUTH;
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testRecordWithRecordValues(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        RecursiveDummyRecord original = new RecursiveDummyRecord("Hello", 12353, new DummyRecord("World", 234));
        //
        RecursiveDummyRecord result = roundTrip(RecursiveDummyRecord.class, original);
        //
        assertThat(result).isEqualTo(original);
    }
}
