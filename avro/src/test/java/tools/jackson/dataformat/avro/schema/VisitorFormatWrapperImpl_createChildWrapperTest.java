package tools.jackson.dataformat.avro.schema;

import tools.jackson.databind.SerializationContext;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroTestBase;

import org.assertj.core.api.Assertions;

public class VisitorFormatWrapperImpl_createChildWrapperTest
extends AvroTestBase
{
    private final AvroMapper MAPPER = newMapper();

    public void testChildWrapper()
    {
        // GIVEN
        SerializationContext ctxt = MAPPER._serializationContext();
        DefinedSchemas schemas = new DefinedSchemas();

        VisitorFormatWrapperImpl src = new VisitorFormatWrapperImpl(schemas, ctxt);
        src.enableLogicalTypes();

        // WHEN
        VisitorFormatWrapperImpl actual = src.createChildWrapper();

        // THEN
        // All settings are inherited from parent visitor wrapper.
        Assertions.assertThat(actual.getSchemas()).isEqualTo(schemas);
        Assertions.assertThat(actual.getContext()).isEqualTo(ctxt);
        Assertions.assertThat(actual.isLogicalTypesEnabled()).isTrue();
    }
}
