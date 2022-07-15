package tools.jackson.dataformat.avro;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.avro.gen.Event35;
import tools.jackson.dataformat.avro.gen.Event35Id;

// Test(s) for [dataformat-avro#35]
public class SerializeGeneratedTest extends AvroTestBase
{
    public void testWriteGeneratedEvent() throws Exception
    {
        Event35 event = new Event35();
        event.setPlayerCount(100);
        Event35Id id = new Event35Id();
        id.setFirst(10);
        ObjectMapper mapper = new AvroMapper();
        byte[] bytes = mapper.writer(new AvroSchema(Event35.SCHEMA$)).writeValueAsBytes(event);
        assertNotNull(bytes);
    }
}
