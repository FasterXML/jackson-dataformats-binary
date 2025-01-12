package tools.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WriteComplexPojoTest extends ProtobufTestBase
{
    private final ObjectMapper MAPPER = new ProtobufMapper();

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    @Test
    public void testMediaItemSimple() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_MEDIA_ITEM);
        final ObjectWriter w = MAPPER.writer(schema);
        byte[] bytes = w.writeValueAsBytes(MediaItem.buildItem());

        assertNotNull(bytes);

        assertEquals(252, bytes.length);

        // let's read back for fun
        MediaItem output = MAPPER.readerFor(MediaItem.class)
                .with(schema)
                .readValue(bytes);
        assertNotNull(output);
    }
}
