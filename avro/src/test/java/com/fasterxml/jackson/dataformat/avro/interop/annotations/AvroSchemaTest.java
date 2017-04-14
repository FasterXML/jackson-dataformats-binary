package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroSchema;
import org.apache.avro.reflect.Nullable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

/**
 * Tests that {@code @AvroSchema} is handled correctly. Specifically:
 * <ul>
 * <li>When present on a type, it completely and wholly overrides any other schema-related information on that type and its fields,
 * including name, meta properties, defaults, and docs</li>
 * <li>When present on a field, it completely and wholly overrides any other schema-related information on that field (but not
 * other properties, like name, defaults, docs, or meta properties)</li>
 * </ul>
 * <p>
 * Additionally, tests that class and property descriptions are picked up correctly when using Jackson annotations
 */
public class AvroSchemaTest extends InteropTestBase {
    @AvroSchema("{\"type\":\"string\"}")
    public static class OverriddenClassSchema {
        public int field;
    }

    @JsonClassDescription("A cool class!")
    public static class OverriddenFieldSchema {
        @AvroSchema("{\"type\":\"int\"}")
        public String myField;

        @Nullable
        @JsonPropertyDescription("the best field in the world")
        public OverriddenClassSchema recursiveOverride;

        @Nullable
        @AvroSchema("{\"type\":\"long\"}")
        public OverriddenClassSchema precedenceField;
    }

    @Test
    public void testJacksonClassDescription() throws Exception {
        Schema schema = ApacheAvroInteropUtil.getJacksonSchema(OverriddenFieldSchema.class);
        //
        assertThat(schema.getDoc()).isEqualTo("A cool class!");
    }

    @Test
    public void testJacksonPropertyDescription() throws Exception {
        Schema schema = ApacheAvroInteropUtil.getJacksonSchema(OverriddenFieldSchema.class);
        //
        assertThat(schema.getField("recursiveOverride").doc()).isEqualTo("the best field in the world");
    }

    @Test
    public void testTypeOverride() throws IOException {
        Schema schema = schemaFunctor.apply(OverriddenClassSchema.class);
        //
        assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    public void testFieldOverride() throws IOException {
        Schema schema = schemaFunctor.apply(OverriddenFieldSchema.class);
        //
        assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(schema.getField("myField").schema().getType()).isEqualTo(Schema.Type.INT);
    }

    @Test
    public void testRecursiveFieldOverride() throws IOException {
        Schema schema = schemaFunctor.apply(OverriddenFieldSchema.class);
        //
        assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(schema.getField("recursiveOverride").schema().getType()).isEqualTo(Schema.Type.UNION);
        assertThat(schema.getField("recursiveOverride").schema().getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
        assertThat(schema.getField("recursiveOverride").schema().getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    public void testOverridePrecedence() throws IOException {
        Schema schema = schemaFunctor.apply(OverriddenFieldSchema.class);
        //
        assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(schema.getField("precedenceField").schema().getType()).isEqualTo(Schema.Type.LONG);
    }
}
