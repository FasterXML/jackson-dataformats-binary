package tools.jackson.dataformat.protobuf;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class WriteComplexPojoTest extends ProtobufTestBase
{
    final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

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
