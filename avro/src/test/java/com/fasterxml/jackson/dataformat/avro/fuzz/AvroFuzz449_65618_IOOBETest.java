package com.fasterxml.jackson.dataformat.avro.fuzz;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroParser;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

// [dataformats-binary#449]
public class AvroFuzz449_65618_IOOBETest extends AvroTestBase
{
    @JsonPropertyOrder({ "name", "value" })
    static class RootType {
        public String name;
        public int value;
    }

    @Test
    public void testFuzz65618IOOBE() throws Exception {
        final AvroFactory factory = AvroFactory.builderWithNativeDecoder().build();
        final AvroMapper mapper = new AvroMapper(factory);

        final byte[] doc = {
            (byte) 2, (byte) 22, (byte) 36, (byte) 2, (byte) 0,
            (byte) 0, (byte) 8, (byte) 3, (byte) 3, (byte) 3,
            (byte) 122, (byte) 3, (byte) -24
        };

        final AvroSchema schema = mapper.schemaFor(RootType.class);
        try (AvroParser p =  (AvroParser) mapper.createParser(doc)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            p.nextToken();
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            verifyException(e, "Malformed 2-byte UTF-8 character at the end of");
        }
    }
}
