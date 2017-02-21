package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import lombok.Data;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroMeta;
import org.apache.avro.reflect.AvroSchema;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroMetaTest extends InteropTestBase {

    @Data
    @AvroMeta(key = "class-meta", value = "class value")
    static class MetaTest {

        @AvroMeta(key = "custom Property", value = "Some Value")
        private String someProperty;

        @AvroMeta(key = "required custom Property", value = "Some required Value")
        @JsonProperty(required = true)
        private String someRequiredProperty;
    }

    @Data
    static class BadMetaTest {

        @AvroMeta(key = "name", value = "colliding property")
        private String types;
    }

    @Data
    @AvroSchema("{\"type\":\"string\"}")
    @AvroMeta(key="overridden", value = "true")
    static class OverriddenClassSchema {

        private int field;
    }

    @Test
    public void testAnnotationPrecedence() {
        Schema schema = schemaFunctor.apply(OverriddenClassSchema.class);
        //
        assertThat(schema.getProp("overridden")).isNull();
    }

    @Test
    public void testOptionalFieldMetaProperty() {
        Schema schema = schemaFunctor.apply(MetaTest.class);
        //
        assertThat(schema.getField("someProperty").getProp("custom Property")).isEqualTo("Some Value");
    }

    @Test
    public void testRequiredFieldMetaProperty() {
        Schema schema = schemaFunctor.apply(MetaTest.class);
        //
        assertThat(schema.getField("someRequiredProperty").getProp("required custom Property")).isEqualTo("Some required Value");
    }

    @Test
    public void testClassMetaProperty() {
        Schema schema = schemaFunctor.apply(MetaTest.class);
        //
        assertThat(schema.getProp("class-meta")).isEqualTo("class value");
    }

    @Test(expected = AvroRuntimeException.class)
    public void testCollidingMeta() {
        schemaFunctor.apply(BadMetaTest.class);
    }

}
