package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing primitive array fields on records
 */
public class RecordWithPrimitiveWrapperArrayTest extends InteropTestBase
{
    public static class TestRecord {
        public Byte[]      byteArrayField      = new Byte[0];
        public Short[]     shortArrayField     = new Short[0];
        public Character[] characterArrayField = new Character[0];
        public Integer[]   integerArrayField   = new Integer[0];
        public Long[]      longArrayField      = new Long[0];
        public Float[]     floatArrayField     = new Float[0];
        public Double[]    doubleArrayField    = new Double[0];
        public String[]    stringArrayField    = new String[0];

        @Override
        public int hashCode() {
            return Objects.hash((Object) byteArrayField);
        }

        @Override
        public String toString() {
            return "TestRecord";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof TestRecord)) return false;
            TestRecord other = (TestRecord) o;
            return Objects.equals(byteArrayField, other.byteArrayField)
                    && Objects.equals(shortArrayField, other.shortArrayField)
                    && Objects.equals(characterArrayField, other.characterArrayField)
                    && Objects.equals(integerArrayField, other.integerArrayField)
                    && Objects.equals(longArrayField, other.longArrayField)
                    && Objects.equals(floatArrayField, other.floatArrayField)
                    && Objects.equals(doubleArrayField, other.doubleArrayField)
                    && Objects.equals(stringArrayField, other.stringArrayField)
                ;
        }
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testByteField(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.byteArrayField = new Byte[]{1, 0, -1, Byte.MIN_VALUE, Byte.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.byteArrayField).isEqualTo(record.byteArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testCharacterField(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.characterArrayField = new Character[]{1, 0, Character.MIN_VALUE, Character.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.characterArrayField).isEqualTo(record.characterArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testDoubleField(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.doubleArrayField = new Double[]{1D, 0D, -1D, Double.MIN_VALUE, Double.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.doubleArrayField).isEqualTo(record.doubleArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testFloatField(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.floatArrayField = new Float[]{1F, 0F, -1F, Float.MIN_VALUE, Float.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.floatArrayField).isEqualTo(record.floatArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testInteger(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.integerArrayField = new Integer[]{1, 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.integerArrayField).isEqualTo(record.integerArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testLongField(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.longArrayField = new Long[]{1L, 0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.longArrayField).isEqualTo(record.longArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testShortField(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.shortArrayField = new Short[]{1, 0, -1, Short.MIN_VALUE, Short.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.shortArrayField).isEqualTo(record.shortArrayField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testStringField(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.stringArrayField = new String[]{"", "one", "HelloWorld"};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.stringArrayField).isEqualTo(record.stringArrayField);
    }
}
