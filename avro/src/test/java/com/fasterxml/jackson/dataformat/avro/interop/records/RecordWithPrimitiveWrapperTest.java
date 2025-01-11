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
 * Tests serializing wrapper types for primitives on records
 */
public class RecordWithPrimitiveWrapperTest extends InteropTestBase
{
    public static class TestRecord {
        public Byte      byteField      = 0;
        public Short     shortField     = 0;
        public Character characterField = 'A';
        public Integer   integerField   = 0;
        public Long      longField      = 0L;
        public Float     floatField     = 0F;
        public Double    doubleField    = 0D;
        public String    stringField    = "";

        @Override
        public int hashCode() {
            return byteField + shortField + characterField + integerField
                    + Objects.hash(stringField);
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
            return Objects.equals(byteField, other.byteField)
                    && Objects.equals(shortField, other.shortField)
                    && Objects.equals(characterField, other.characterField)
                    && Objects.equals(integerField, other.integerField)
                    && Objects.equals(longField, other.longField)
                    && Objects.equals(floatField, other.floatField)
                    && Objects.equals(doubleField, other.doubleField)
                    && Objects.equals(stringField, other.stringField)
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
        TestRecord result = roundTrip(record);
        assertThat(result.byteField).isEqualTo(record.byteField);
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

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testStringField(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        
        TestRecord record = new TestRecord();
        record.stringField = "Hello World";
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.stringField).isEqualTo(record.stringField);
    }

}
