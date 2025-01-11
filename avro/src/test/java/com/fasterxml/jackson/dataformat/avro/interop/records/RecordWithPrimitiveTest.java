package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.lang.reflect.Type;

import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing primitive fields on records
 */
public class RecordWithPrimitiveTest extends InteropTestBase
{
    @JsonPropertyOrder({ "byteField", "shortField", "characterField", "integerField", "longField",
        "floatField", "doubleField" })
    public static class TestRecord {
        public byte   byteField;
        public short  shortField;
        public char   characterField;
        public int    integerField;
        public long   longField;
        public float  floatField;
        public double doubleField;

        @Override
        public int hashCode() {
            return byteField + shortField + characterField + integerField
                    + (int) longField;
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
            return (byteField == other.byteField)
                    && (shortField == other.shortField)
                    && (characterField == other.characterField)
                    && (integerField == other.integerField)
                    && (longField == other.longField)
                    && (floatField == other.floatField)
                    && (doubleField == other.doubleField)
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
        record.byteField = Byte.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.byteField).isEqualTo(record.byteField);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testCharacterField(
           Function<Type, Schema> schemaFunctor,
           BiFunction<Schema, Object, byte[]> serializeFunctor,
           BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        TestRecord record = new TestRecord();
        record.characterField = Character.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.characterField).isEqualTo(record.characterField);
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
        record.doubleField = Double.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.doubleField).isEqualTo(record.doubleField);
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
        record.floatField = Float.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.floatField).isEqualTo(record.floatField);
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
        record.integerField = Integer.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.integerField).isEqualTo(record.integerField);
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
        record.longField = Long.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.longField).isEqualTo(record.longField);
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
        record.shortField = Short.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.shortField).isEqualTo(record.shortField);
    }
}
