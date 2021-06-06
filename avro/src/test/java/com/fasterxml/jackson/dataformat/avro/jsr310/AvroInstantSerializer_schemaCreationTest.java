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
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class AvroInstantSerializer_schemaCreationTest {

    @Parameter
    public Class testClass;

    @Parameters(name = "With {0}")
    public static Collection<Class> testData() {
        return Arrays.asList(
                Instant.class,
                OffsetDateTime.class,
                ZonedDateTime.class);
    }

    @Test
    public void testSchemaCreation() throws JsonMappingException {
        // GIVEN
        AvroMapper mapper = AvroMapper.builder()
                .addModules(new AvroJavaTimeModule())
                .build();
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        // WHEN
        mapper.acceptJsonFormatVisitor(testClass, gen);
        Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.LONG);
        assertThat(actualSchema.getProp(LogicalType.LOGICAL_TYPE_PROP)).isEqualTo("timestamp-millis");
        /**
         * Having logicalType and java-class is not valid according to
         * {@link org.apache.avro.LogicalType#validate(Schema)}
         */
        assertThat(actualSchema.getProp(SpecificData.CLASS_PROP)).isNull();
    }

}
