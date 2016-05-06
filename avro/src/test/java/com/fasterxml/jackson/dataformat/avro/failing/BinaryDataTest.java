package com.fasterxml.jackson.dataformat.avro.failing;

import java.io.IOException;

import com.fasterxml.jackson.dataformat.avro.*;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

public class BinaryDataTest extends AvroTestBase
{
    static class FilePojo {
        protected FilePojo() { }
        public FilePojo(String text) throws IOException {
            filedata = text.getBytes("UTF-8");
        }
        
        public String filename = "TestFile.txt";
        public byte[] filedata;
    }

    public void testAvroSchemaGenerationWithJackson() throws Exception
    {
        AvroMapper mapper = new AvroMapper();
        AvroSchemaGenerator visitor = new AvroSchemaGenerator();
        mapper.acceptJsonFormatVisitor(FilePojo.class, visitor);
        AvroSchema schema = visitor.getGeneratedSchema();

        byte[] ser = mapper.writer(schema).writeValueAsBytes(new FilePojo("ABC"));
        assertNotNull(ser);
        
        // plus should probably also read back, right?
        FilePojo result = mapper.readerFor(FilePojo.class)
                .with(schema)
                .readValue(ser);
        assertNotNull(result);
        assertNotNull(result.filedata);
        assertEquals(3, result.filedata.length);
   }
}
