package com.fasterxml.jackson.dataformat.avro.jsr310;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class AvroJavaTimeModule_schemaCreationTest {

    @Parameter(0)
    public Class testClass;

    @Parameter(1)
    public Schema.Type expectedType;

    @Parameter(2)
    public String expectedLogicalType;

    @Parameters(name = "With {0}")
    public static Collection testData() {
        return Arrays.asList(new Object[][]{
                // Java type    | expected Avro type    | expected logicalType
                {Instant.class, Schema.Type.LONG, "timestamp-millis"},
                {OffsetDateTime.class, Schema.Type.LONG, "timestamp-millis"},
                {ZonedDateTime.class, Schema.Type.LONG, "timestamp-millis"},
                {LocalDateTime.class, Schema.Type.LONG, "local-timestamp-millis"},
                {LocalDate.class, Schema.Type.INT, "date"},
                {LocalTime.class, Schema.Type.INT, "time-millis"},
        });
    }

    @Test
    public void testSchemaCreation() throws JsonMappingException {
        // GIVEN
        AvroMapper mapper = AvroMapper.builder()
                .addModule(new AvroJavaTimeModule())
                .build();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        gen.enableLogicalTypes();

        // WHEN
        mapper.acceptJsonFormatVisitor(testClass, gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

//        System.out.println(testClass.getName() + " schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(expectedType);
        assertThat(actualSchema.getProp(LogicalType.LOGICAL_TYPE_PROP)).isEqualTo(expectedLogicalType);
        /**
         * Having logicalType and java-class is not valid according to
         * {@link LogicalType#validate(Schema)}
         */
        assertThat(actualSchema.getProp(SpecificData.CLASS_PROP)).isNull();
    }

}
