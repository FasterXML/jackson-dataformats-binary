package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

public class RootEmptyRecord177Test extends AvroTestBase
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
