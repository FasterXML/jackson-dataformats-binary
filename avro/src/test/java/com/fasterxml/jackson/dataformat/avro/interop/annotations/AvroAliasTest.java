package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.reflect.AvroAlias;
import org.apache.avro.reflect.Nullable;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.AvroTestBase;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroAliasTest extends InteropTestBase {

    @AvroAlias(alias = "Employee", space = "com.fasterxml.jackson.dataformat.avro.AvroTestBase$")
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

    @AvroAlias(alias = "Size", space = "com.fasterxml.jackson.dataformat.avro.AvroTestBase$")
    public static enum NewSize {
        SMALL,
        LARGE;
    }

    @AvroAlias(alias = "NewestSize")
    public static enum NewerSize {
        SMALL,
        LARGE;
    }

    @AvroAlias(alias = "NewerSize")
    public static enum NewestSize {
        SMALL,
        LARGE;
    }

    @Test
    public void testAliasedRecordForwardsCompatible() throws IOException {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Employee.class);
        Schema newSchema = schemaFunctor.apply(NewEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @Test
    public void testAliasedRecordBackwardsCompatible() throws IOException {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Employee.class);
        Schema newSchema = schemaFunctor.apply(NewEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    public void testAliasedRecordForwardsCompatibleSameNamespace() throws IOException {
        Schema oldSchema = schemaFunctor.apply(NewEmployee.class);
        Schema newSchema = schemaFunctor.apply(AliasedNameEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @Test
    public void testAliasedRecordBackwardsCompatibleSameNamespace() throws IOException {
        Schema oldSchema = schemaFunctor.apply(NewEmployee.class);
        Schema newSchema = schemaFunctor.apply(AliasedNameEmployee.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    public void testAliasedEnumForwardsCompatible() throws IOException {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Size.class);
        Schema newSchema = schemaFunctor.apply(NewSize.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @Test
    public void testAliasedEnumBackwardsCompatible() throws IOException {
        Schema oldSchema = schemaFunctor.apply(AvroTestBase.Size.class);
        Schema newSchema = schemaFunctor.apply(NewSize.class);
        //
        SchemaCompatibility.SchemaPairCompatibility compatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        //
        assertThat(compatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    public void testAliasedEnumForwardsAndBackwardsCompatible() throws IOException {
        Schema oldSchema = schemaFunctor.apply(NewerSize.class);
        Schema newSchema = schemaFunctor.apply(NewestSize.class);
        //
        SchemaCompatibility.SchemaPairCompatibility backwardsCompatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(oldSchema, newSchema);
        SchemaCompatibility.SchemaPairCompatibility forwardsCompatibility =
            SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
        //
        assertThat(backwardsCompatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
        assertThat(forwardsCompatibility.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

}
