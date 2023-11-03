package tools.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

public interface SchemaBuilder {
    Schema builtAvroSchema();
}
