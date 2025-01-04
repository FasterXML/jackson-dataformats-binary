package tools.jackson.dataformat.avro;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.dataformat.avro.annotation.AvroDecimal;
import tools.jackson.dataformat.avro.annotation.AvroFixedSize;
import tools.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import static org.assertj.core.api.Assertions.assertThat;

public class BigDecimal_schemaCreationTest extends AvroTestBase {
    private static final AvroMapper MAPPER = new AvroMapper();

    static class BigDecimalWithAvroDecimalAnnotationWrapper {
        @JsonProperty(required = true) // field is required to have simpler avro schema
        @AvroDecimal(precision = 10, scale = 2)
        public final BigDecimal bigDecimalValue;

        public BigDecimalWithAvroDecimalAnnotationWrapper(BigDecimal bigDecimalValue) {
            this.bigDecimalValue = bigDecimalValue;
        }
    }

    @Test
    public void testSchemaCreation_withLogicalTypesDisabled_onBigDecimalWithAvroDecimalAnnotation()
        throws Exception
    {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .disableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithAvroDecimalAnnotationWrapper.class, gen);
        // actualSchema = MAPPER.schemaFor(BigDecimalWithAvroDecimalAnnotationWrapper.class) would be enough in this case
        // because logical types are disabled by default.
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithAvroDecimalAnnotationWrapper.class.getSimpleName() + " schema:" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();
        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(bigDecimalValue.getLogicalType()).isNull();
        assertThat(bigDecimalValue.getProp("java-class")).isEqualTo("java.math.BigDecimal");
    }

    @Test
    public void testSchemaCreation_withLogicalTypesEnabled_onBigDecimalWithAvroDecimalAnnotation()
        throws Exception
    {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .enableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithAvroDecimalAnnotationWrapper.class, gen);
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithAvroDecimalAnnotationWrapper.class.getSimpleName() + " schema:" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();
        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.BYTES);
        assertThat(bigDecimalValue.getLogicalType()).isEqualTo(LogicalTypes.decimal(10, 2));
        assertThat(bigDecimalValue.getProp("java-class")).isNull();
    }

    static class BigDecimalWithAvroDecimalAnnotationToFixedWrapper {
        @JsonProperty(required = true) // field is required to have simpler avro schema
        @AvroFixedSize(typeName = "BigDecimalWithAvroDecimalAnnotationToFixedWrapper", size = 10)
        @AvroDecimal(precision = 6, scale = 3)
        public final BigDecimal bigDecimalValue;

        public BigDecimalWithAvroDecimalAnnotationToFixedWrapper(BigDecimal bigDecimalValue) {
            this.bigDecimalValue = bigDecimalValue;
        }
    }

    @Test
    public void testSchemaCreation_withLogicalTypesEnabled_onBigDecimalWithAvroDecimalAnnotationToFixed()
        throws Exception
    {
        // GIVEN
        AvroSchemaGenerator gen = new AvroSchemaGenerator()
                .enableLogicalTypes();

        // WHEN
        MAPPER.acceptJsonFormatVisitor(BigDecimalWithAvroDecimalAnnotationToFixedWrapper.class, gen);
        final Schema actualSchema = gen.getGeneratedSchema().getAvroSchema();

        System.out.println(BigDecimalWithAvroDecimalAnnotationToFixedWrapper.class.getSimpleName() + " schema:" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getField("bigDecimalValue")).isNotNull();

        Schema bigDecimalValue = actualSchema.getField("bigDecimalValue").schema();
        assertThat(bigDecimalValue.getType()).isEqualTo(Schema.Type.FIXED);
        assertThat(bigDecimalValue.getFixedSize()).isEqualTo(10);
        assertThat(bigDecimalValue.getLogicalType()).isEqualTo(LogicalTypes.decimal(6, 3));
        assertThat(bigDecimalValue.getProp("java-class")).isNull();
    }
}
