package tools.jackson.dataformat.protobuf.fuzz;

import org.junit.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.dataformat.protobuf.*;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProtobufFuzz_65674_NPETest
{
    private final ProtobufMapper MAPPER = new ProtobufMapper();

    @Test
    public void testFuzz65674NPE() throws Exception {
        final byte[] doc = new byte[0];
        final ProtobufSchema schema = MAPPER.generateSchemaFor(getClass());
        try (JsonParser p = MAPPER.reader().with(schema).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertNull(p.nextToken());
        }
    }
}
