package com.fasterxml.jackson.dataformat.avro.failing;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

public class EmptyRecordRead177Test extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    private final AvroSchema SCHEMA = parseSchema(MAPPER,
"{'type':'record', 'name':'Empty','namespace':'something','fields':[]}");

    @JsonAutoDetect // just a marker to avoid "no properties found" exception
    static final class Empty {
        @Override
        public boolean equals(Object o) {
            return o instanceof Empty;
        }
    }

    public void testEmptyRecord() throws Exception {
        final Empty empty = new Empty();

        byte[] ser = MAPPER.writer().with(SCHEMA).writeValueAsBytes(empty);

        final Empty result = MAPPER.readerFor(Empty.class)
                .with(SCHEMA)
                .readValue(ser);

        assertEquals(empty, result);
    }
}
