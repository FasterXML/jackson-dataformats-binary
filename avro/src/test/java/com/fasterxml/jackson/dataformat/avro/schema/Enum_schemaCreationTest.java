package com.fasterxml.jackson.dataformat.avro.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.Test;

public class Enum_schemaCreationTest extends AvroTestBase {

    static enum NumbersEnum {
    	ONE, TWO, THREE
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testJavaEnumToAvroEnum_test() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(NumbersEnum.class , gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println("schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo( Schema.Type.ENUM);
        assertThat(actualSchema.getEnumSymbols()).containsExactlyInAnyOrder("ONE", "TWO", "THREE");
    }

    @Test
    public void testJavaEnumToAvroString_test() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
        		.enableWriteEnumAsString();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(NumbersEnum.class , gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println("schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo( Schema.Type.STRING);

        // When type is stringable then java-class property is addded.
        assertThat(actualSchema.getProp(SpecificData.CLASS_PROP)).isNotEmpty();
    }

}
