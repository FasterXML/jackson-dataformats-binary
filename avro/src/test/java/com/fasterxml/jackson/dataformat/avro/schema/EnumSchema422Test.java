package com.fasterxml.jackson.dataformat.avro.schema;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonValue;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

// For [dataformats-binary#422]
public class EnumSchema422Test extends AvroTestBase
{
    enum EnumType422 {
        CARD_S("CARD-S");

        private final String value;

        EnumType422(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }
    }

    static class Wrapper422 {
        public EnumType422 contract;
    }

    private final AvroMapper MAPPER = newMapper();

    // For [dataformats-binary#422]
    @Test
    public void testEnumSchemaGeneration422() throws Exception
    {
        // First, failure due to invalid enum value (when generating as Enum)
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        try {
            MAPPER.acceptJsonFormatVisitor(Wrapper422.class, gen);
            fail("Expected failure");
        } catch (IllegalArgumentException e) { // in 2.x
            verifyException(e, "Problem generating Avro `Schema` for Enum type");
            verifyException(e, "Illegal character in");
        }

        // But then success when configuring to produce Strings for Enum types

        gen = new AvroSchemaGenerator()
                .enableWriteEnumAsString();
        MAPPER.acceptJsonFormatVisitor(Wrapper422.class, gen);

        org.apache.avro.Schema avroSchema = gen.getGeneratedSchema().getAvroSchema();
        String avroSchemaInJSON = avroSchema.toString(true);
        assertNotNull(avroSchemaInJSON);
    }
}
