package tools.jackson.dataformat.avro.schema;

import org.junit.Test;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

import static org.assertj.core.api.Assertions.assertThat;

public class UUIDVisitor_builtAvroSchemaTest {

    @Test
    public void testLogicalTypesDisabled() {
        // GIVEN
        boolean logicalTypesEnabled = false;
        UUIDVisitor uuidVisitor = new UUIDVisitor(logicalTypesEnabled);

        // WHEN
        Schema actualSchema = uuidVisitor.builtAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.FIXED);
        assertThat(actualSchema.getFixedSize()).isEqualTo(16);
        assertThat(actualSchema.getName()).isEqualTo("UUID");
        assertThat(actualSchema.getNamespace()).isEqualTo("java.util");
        assertThat(actualSchema.getProp(LogicalType.LOGICAL_TYPE_PROP)).isNull();
    }

    @Test
    public void testLogicalTypesEnabled() {
        // GIVEN
        boolean logicalTypesEnabled = true;
        UUIDVisitor uuidVisitor = new UUIDVisitor(logicalTypesEnabled);

        // WHEN
        Schema actualSchema = uuidVisitor.builtAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.FIXED);
        assertThat(actualSchema.getFixedSize()).isEqualTo(16);
        assertThat(actualSchema.getName()).isEqualTo("UUID");
        assertThat(actualSchema.getNamespace()).isEqualTo("java.util");
        assertThat(actualSchema.getProp(LogicalType.LOGICAL_TYPE_PROP)).isEqualTo("uuid");
    }

}
