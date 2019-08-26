package com.fasterxml.jackson.dataformat.avro.failing;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

public class EmptyRecordRead177Test extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    private Schema SCHEMA = SchemaBuilder
            .builder(EmptyRecordRead177Test.class.getName() + "$")
            .record(Empty.class.getSimpleName())
                .fields()
            .endRecord();

    @JsonAutoDetect // just a marker to avoid "no properties found" exception
    static final class Empty {
        @Override
        public boolean equals(Object o) {
            return o instanceof Empty;
        }
    }

    public void testEmptyRecord() throws Exception {
        final Empty empty = new Empty();
        
        byte[] ser = MAPPER.writer().with(new AvroSchema(SCHEMA)).writeValueAsBytes(empty);

        final Empty result = MAPPER.readerFor(Empty.class)
                .with(new AvroSchema(SCHEMA))
                .readValue(ser);

        assertEquals(empty, result);
    }
}
