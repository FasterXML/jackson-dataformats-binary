package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.annotation.Decimal;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.junit.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class BigDecimal_schemaCreationTest extends AvroTestBase {
    private static final AvroMapper MAPPER = new AvroMapper();

    static class BigDecimalWithDecimalAnnotationWrapper {
        @JsonProperty(required = true) // field is required to have simpler avro schema
        @Decimal(precision = 10, scale = 2)
        public final BigDecimal bigDecimalValue;

        public BigDecimalWithDecimalAnnotationWrapper(BigDecimal bigDecimalValue) {
            this.bigDecimalValue = bigDecimalValue;
        }
    }

    @Test
    public void testSchemaCreation_withLogicalTypesDisabled_onBigDecimalWithDecimalAnnotation() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .disableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithDecimalAnnotationWrapper.class, gen);
        // actualSchema = MAPPER.schemaFor(BigDecimalWithDecimalAnnotationWrapper.class) would be enough in this case
        // because logical types are disabled by default.
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithDecimalAnnotationWrapper.class.getSimpleName() + " schema:" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();
        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(bigDecimalValue.getLogicalType()).isNull();
        assertThat(bigDecimalValue.getProp("java-class")).isEqualTo("java.math.BigDecimal");
    }

    @Test
    public void testSchemaCreation_withLogicalTypesEnabled_onBigDecimalWithDecimalAnnotation() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .enableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithDecimalAnnotationWrapper.class, gen);
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithDecimalAnnotationWrapper.class.getSimpleName() + " schema:" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();
        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.BYTES);
        assertThat(bigDecimalValue.getLogicalType()).isEqualTo(LogicalTypes.decimal(10, 2));
        assertThat(bigDecimalValue.getProp("java-class")).isNull();
    }

    static class BigDecimalWithDecimalAnnotationToFixedWrapper {
        @JsonProperty(required = true) // field is required to have simpler avro schema
        @AvroFixedSize(typeName = "BigDecimalWithDecimalAnnotationToFixedWrapper", size = 10)
        @Decimal(precision = 6, scale = 3)
        public final BigDecimal bigDecimalValue;

        public BigDecimalWithDecimalAnnotationToFixedWrapper(BigDecimal bigDecimalValue) {
            this.bigDecimalValue = bigDecimalValue;
        }
    }

    @Test
    public void testSchemaCreation_withLogicalTypesEnabled_onBigDecimalWithDecimalAnnotationToFixed() throws JsonMappingException {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .enableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithDecimalAnnotationToFixedWrapper.class, gen);
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithDecimalAnnotationToFixedWrapper.class.getSimpleName() + " schema:" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();

        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.FIXED);
        assertThat(bigDecimalValue.getFixedSize()).isEqualTo(10);
        assertThat(bigDecimalValue.getLogicalType()).isEqualTo(LogicalTypes.decimal(6, 3));
        assertThat(bigDecimalValue.getProp("java-class")).isNull();
    }
}
