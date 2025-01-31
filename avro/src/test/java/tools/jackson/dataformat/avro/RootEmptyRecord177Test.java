package tools.jackson.dataformat.avro;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import tools.jackson.databind.DeserializationFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RootEmptyRecord177Test extends AvroTestBase
{
    @JsonAutoDetect // just a marker to avoid "no properties found" exception
    public static final class Empty {
        @Override
        public boolean equals(Object o) {
            return o instanceof Empty;
        }
    }

    @Test
    public void testEmptyRecord() throws Exception
    {
        // 30-Jan-2025, tatu: For some reason, we get "endless stream" of
        //   empty Objects for 0 byte size records. For now, work aroudn
        final AvroMapper mapper = mapperBuilder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();

        final AvroSchema SCHEMA = parseSchema(mapper,
    "{'type':'record', 'name':'Empty','namespace':'something','fields':[]}");

        final Empty empty = new Empty();

        byte[] ser = mapper.writer().with(SCHEMA).writeValueAsBytes(empty);
        final Empty result = mapper.readerFor(Empty.class)
                .with(SCHEMA)
                .readValue(ser);

        assertEquals(empty, result);
    }
}
