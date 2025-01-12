package tools.jackson.dataformat.avro.jsr310;

import java.time.*;
import java.util.Arrays;
import java.util.Collection;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.schema.AvroSchemaGenerator;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroJavaTimeModule_schemaCreationTest {

    public static Collection<Object[]> testData() {
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

    @ParameterizedTest(name = "With {0}")
    @MethodSource("testData")
    public void testSchemaCreation(Class<?> testClass,
       Schema.Type expectedType, String expectedLogicalType)
            throws Exception
    {
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
