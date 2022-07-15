package tools.jackson.dataformat.avro.annotation;

import org.apache.avro.Schema;

import org.junit.Test;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.schema.AvroSchemaGenerator;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroNamespaceTest {

    static class ClassWithoutAvroNamespaceAnnotation {
    }

    @AvroNamespace("ClassWithAvroNamespaceAnnotation.namespace")
    static class ClassWithAvroNamespaceAnnotation {
    }

    enum EnumWithoutAvroNamespaceAnnotation {FOO, BAR;}

    @AvroNamespace("EnumWithAvroNamespaceAnnotation.namespace")
    enum EnumWithAvroNamespaceAnnotation {FOO, BAR;}

    @Test
    public void class_without_AvroNamespace_test() throws Exception {
        // GIVEN
        AvroMapper mapper = new AvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        // WHEN
        mapper.acceptJsonFormatVisitor(ClassWithoutAvroNamespaceAnnotation.class, gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        // THEN
        assertThat(actualSchema.getNamespace())
                .isEqualTo("tools.jackson.dataformat.avro.annotation.AvroNamespaceTest$");
    }

    @Test
    public void class_with_AvroNamespace_test() throws Exception {
        // GIVEN
        AvroMapper mapper = new AvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        // WHEN
        mapper.acceptJsonFormatVisitor(ClassWithAvroNamespaceAnnotation.class, gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        // THEN
        assertThat(actualSchema.getNamespace())
                .isEqualTo("ClassWithAvroNamespaceAnnotation.namespace");
    }

    @Test
    public void enum_without_AvroNamespace_test() throws Exception {
        // GIVEN
        AvroMapper mapper = new AvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        // WHEN
        mapper.acceptJsonFormatVisitor(EnumWithoutAvroNamespaceAnnotation.class, gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        // THEN
        assertThat(actualSchema.getNamespace())
                .isEqualTo("tools.jackson.dataformat.avro.annotation.AvroNamespaceTest$");
    }

    @Test
    public void enum_with_AvroNamespace_test() throws Exception {
        // GIVEN
        AvroMapper mapper = new AvroMapper();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        // WHEN
        mapper.acceptJsonFormatVisitor(EnumWithAvroNamespaceAnnotation.class, gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        // THEN
        assertThat(actualSchema.getNamespace())
                .isEqualTo("EnumWithAvroNamespaceAnnotation.namespace");
    }

}
