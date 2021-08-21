package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

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
