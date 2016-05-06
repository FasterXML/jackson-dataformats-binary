package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.gen.Event35;
import com.fasterxml.jackson.dataformat.avro.gen.Event35Id;

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
