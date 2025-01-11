package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;
import java.lang.reflect.Type;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.reflect.AvroAlias;
import org.apache.avro.reflect.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroAliasTest extends InteropTestBase {

    @AvroAlias(alias = "Employee", space = "com.fasterxml.jackson.dataformat.avro.AvroTestBase")
    public static class NewEmployee {

        public String name;

        public int age;

        public String[] emails;

        public NewEmployee boss;
    }

    @AvroAlias(alias = "NewEmployee")
    public static class AliasedNameEmployee {

        public String name;

        public int age;

        public String[] emails;

        @Nullable
        public AliasedNameEmployee boss;
    }

    @AvroAlias(alias = "Size", space = "com.fasterxml.jackson.dataformat.avro.AvroTestBase")
    public enum NewSize {
        SMALL,
        LARGE;
    }

    @AvroAlias(alias = "NewestSize")
    public enum NewerSize {
        SMALL,
        LARGE;
    }

    @AvroAlias(alias = "NewerSize")
    public enum NewestSize {
        SMALL,
        LARGE;
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedRecordForwardsCompatible(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Employee.class);
        Schema newSchema = schemaFunctor.apply(NewEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
                SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        checkSchemaIsCompatible(compatibility);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedRecordBackwardsCompatible(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Employee.class);
        Schema newSchema = schemaFunctor.apply(NewEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
                SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedRecordForwardsCompatibleSameNamespace(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(NewEmployee.class);
        Schema newSchema = schemaFunctor.apply(AliasedNameEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        checkSchemaIsCompatible(compatibility);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedRecordBackwardsCompatibleSameNamespace(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(NewEmployee.class);
        Schema newSchema = schemaFunctor.apply(AliasedNameEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedEnumForwardsCompatible(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Size.class);
        Schema newSchema = schemaFunctor.apply(NewSize.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        checkSchemaIsCompatible(compatibility);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedEnumBackwardsCompatible(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Size.class);
        Schema newSchema = schemaFunctor.apply(NewSize.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testAliasedEnumForwardsAndBackwardsCompatible(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        Schema oldSchema = schemaFunctor.apply(NewerSize.class);
        Schema newSchema = schemaFunctor.apply(NewestSize.class);
        //
        SchemaCompatibility.SchemaPairCompatibility backwardsCompatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        SchemaCompatibility.SchemaPairCompatibility forwardsCompatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        checkSchemaIsCompatible(backwardsCompatibility);
        checkSchemaIsCompatible(forwardsCompatibility);
    }

    private void checkSchemaIsCompatible(SchemaCompatibility.SchemaPairCompatibility compatibility) {
        assertThat(compatibility.getType())
            .withFailMessage("Expected schema to be compatible but was not. Reason:\n%s", compatibility.getDescription())
            .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

}
