package tools.jackson.dataformat.avro.schema;

import tools.jackson.databind.SerializerProvider;

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
        SerializerProvider serializerProvider = MAPPER._serializerProvider();
        DefinedSchemas schemas = new DefinedSchemas();

        VisitorFormatWrapperImpl src = new VisitorFormatWrapperImpl(schemas, serializerProvider);
        src.enableLogicalTypes();

        // WHEN
        VisitorFormatWrapperImpl actual = src.createChildWrapper();

        // THEN
        // All settings are inherited from parent visitor wrapper.
        Assertions.assertThat(actual.getSchemas()).isEqualTo(schemas);
        Assertions.assertThat(actual.getProvider()).isEqualTo(serializerProvider);
        Assertions.assertThat(actual.isLogicalTypesEnabled()).isTrue();
    }
}
