package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.annotation.Decimal;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.junit.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class BigDecimalTest extends AvroTestBase
{
    private static final AvroMapper MAPPER = new AvroMapper();

    static class BigDecimalWithDecimalAnnotationToBytesWrapper {
        @JsonProperty(required = true) // field is made required only to have simpler avro schema
        @Decimal(precision = 10, scale = 2)
        public BigDecimal bigDecimalValue;

        public BigDecimalWithDecimalAnnotationToBytesWrapper(BigDecimal bigDecimalValue) {
            this.bigDecimalValue = bigDecimalValue;
        }
    }

    @Test
    public void testSchemaCreationOnBigDecimalWithDecimalAnnotationToBytes() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .enableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithDecimalAnnotationToBytesWrapper.class, gen);
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithDecimalAnnotationToBytesWrapper.class.getSimpleName() + " schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();

        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.BYTES);
        assertThat(bigDecimalValue.getLogicalType()).isEqualTo(LogicalTypes.decimal(10, 2));
        assertThat(bigDecimalValue.getProp("java-class")).isNull();
    }

    static class BigDecimalWithDecimalAnnotationToFixedWrapper {
        @JsonProperty(required = true) // field is made required only to have simpler avro schema
        @AvroFixedSize(typeName = "BigDecimalWithDecimalAnnotationToFixedWrapper", size = 10)
        @Decimal(precision = 6, scale = 3)
        public BigDecimal bigDecimalValue;

        public BigDecimalWithDecimalAnnotationToFixedWrapper(BigDecimal bigDecimalValue) {
            this.bigDecimalValue = bigDecimalValue;
        }
    }

    @Test
    public void testSchemaCreationOnBigDecimalWithDecimalAnnotationToFixed() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .enableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithDecimalAnnotationToFixedWrapper.class, gen);
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithDecimalAnnotationToFixedWrapper.class.getSimpleName() + " schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();

        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.FIXED);
        assertThat(bigDecimalValue.getFixedSize()).isEqualTo(10);
        assertThat(bigDecimalValue.getLogicalType()).isEqualTo(LogicalTypes.decimal(6, 3));
                assertThat(bigDecimalValue.getProp("java-class")).isNull();
    }

    public static class NamedAmount {
        public final String name;
        public final BigDecimal amount;

        @JsonCreator
        public NamedAmount(@JsonProperty("name") String name,
                           @JsonProperty("amount") double amount) {
            this.name = name;
            this.amount = BigDecimal.valueOf(amount);
        }
    }

    public void testSerializeBigDecimal() throws Exception {
        AvroSchema schema = MAPPER.schemaFor(NamedAmount.class);

        byte[] bytes = MAPPER.writer(schema)
                .writeValueAsBytes(new NamedAmount("peter", 42.0));

        NamedAmount result = MAPPER.reader(schema).forType(NamedAmount.class).readValue(bytes);

        assertEquals("peter", result.name);
        assertEquals(BigDecimal.valueOf(42.0), result.amount);
    }
}
