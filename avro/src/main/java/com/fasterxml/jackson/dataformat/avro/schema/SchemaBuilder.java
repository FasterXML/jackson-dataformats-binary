package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

public interface SchemaBuilder {
    public Schema builtAvroSchema();
}
