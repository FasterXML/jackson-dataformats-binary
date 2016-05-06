package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * Class that can generate an {@link AvroSchema} for a given Java POJO,
 * using definitions Jackson would use for serialization.
 * An instance is typically given to
 * {@link com.fasterxml.jackson.databind.ObjectMapper#acceptJsonFormatVisitor}
 * which will invoke necessary callbacks.
 */
public class AvroSchemaGenerator extends VisitorFormatWrapperImpl
{
    public AvroSchemaGenerator() {
        // NOTE: null is fine here, as provider links itself after construction
        super(new DefinedSchemas(), null);
    }

    public AvroSchema getGeneratedSchema() {
        return new AvroSchema(getAvroSchema());
    }
}
