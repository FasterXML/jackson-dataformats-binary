package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * Class that can generate an {@link AvroSchema} for a given Java POJO,
 * using definitions Jackson would use for serialization.
 * An instance is typically given to
 * {@link com.fasterxml.jackson.databind.ObjectMapper#acceptJsonFormatVisitor}
 * which will invoke necessary callbacks.
 *<p>
 * For default configuration of things like "Logical Types support", see Javadocs
 * of {@link VisitorFormatWrapperImpl}.
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

    @Override
    public AvroSchemaGenerator enableLogicalTypes() {
        super.enableLogicalTypes();
        return this;
    }

    @Override
    public AvroSchemaGenerator disableLogicalTypes() {
        super.disableLogicalTypes();
        return this;
    }

    @Override
    public AvroSchemaGenerator enableWriteEnumAsString() {
        super.enableWriteEnumAsString();
        return this;
    }

    @Override
    public AvroSchemaGenerator disableWriteEnumAsString() {
        super.disableWriteEnumAsString();
        return this;
    }

}
