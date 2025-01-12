package com.fasterxml.jackson.dataformat.avro.schema;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;

public class VisitorFormatWrapperImpl_createChildWrapperTest {

    @Test
    public void test () {
        // GIVEN
        SerializerProvider serializerProvider = new DefaultSerializerProvider.Impl();
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
