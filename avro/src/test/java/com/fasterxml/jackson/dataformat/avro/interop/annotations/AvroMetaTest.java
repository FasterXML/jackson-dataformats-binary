package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroMeta;
import org.apache.avro.reflect.AvroSchema;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class AvroMetaTest extends InteropTestBase
{
    @AvroMeta(key = "class-meta", value = "class value")
    static class MetaTest {

        @AvroMeta(key = "custom Property", value = "Some Value")
        public String someProperty;

        @AvroMeta(key = "required custom Property", value = "Some required Value")
        @JsonProperty(required = true)
        public String someRequiredProperty;
    }

    static class BadMetaTest {
        @AvroMeta(key = "name", value = "colliding property")
        public String types;
    }

    @AvroSchema("{\"type\":\"string\"}")
    @AvroMeta(key="overridden", value = "true")
    static class OverriddenClassSchema {
        public int field;
    }

    @Test
    public void testAnnotationPrecedence() throws Exception {
        Schema schema = schemaFunctor.apply(OverriddenClassSchema.class);
        //
        assertThat(schema.getProp("overridden")).isNull();
    }

    @Test
    public void testOptionalFieldMetaProperty() throws Exception {
        Schema schema = schemaFunctor.apply(MetaTest.class);
        //
        assertThat(schema.getField("someProperty").getProp("custom Property")).isEqualTo("Some Value");
    }

    @Test
    public void testRequiredFieldMetaProperty() throws Exception {
        Schema schema = schemaFunctor.apply(MetaTest.class);
        //
        assertThat(schema.getField("someRequiredProperty").getProp("required custom Property")).isEqualTo("Some required Value");
    }

    @Test
    public void testClassMetaProperty() throws Exception {
        Schema schema = schemaFunctor.apply(MetaTest.class);
        //
        assertThat(schema.getProp("class-meta")).isEqualTo("class value");
    }

//    @Test(expected = AvroRuntimeException.class)
    @Test
    public void testCollidingMeta() throws Exception {
        try {
            schemaFunctor.apply(BadMetaTest.class);
            fail("Should not pass");
        // 27-May-2021, tatu: was at some point "AvroRuntimeException", not so with 2.13:
        } catch (InvalidDefinitionException e) {
            String msg = e.getMessage();
            if (!msg.startsWith("Failed to generate")) {
                fail("Excepted exception message to start with 'Failed to generate', got: ["+msg+"]");
            }
        }
    }
}
