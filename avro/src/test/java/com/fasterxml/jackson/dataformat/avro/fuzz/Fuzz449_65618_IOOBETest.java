package com.fasterxml.jackson.dataformat.avro.fuzz;

import org.junit.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroFactoryBuilder;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroParser;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// [dataformats-binary#449]
public class Fuzz449_65618_IOOBETest
{
    @Test
    public void testFuzz65618IOOBE() throws Exception {
        final AvroFactory factory = AvroFactory.builderWithApacheDecoder().build();
        final AvroMapper mapper = new AvroMapper(factory);

        final byte[] doc = {
            (byte) 2, (byte) 22, (byte) 36, (byte) 2, (byte) 0,
            (byte) 0, (byte) 8, (byte) 3, (byte) 3, (byte) 3,
            (byte) 122, (byte) 3, (byte) -24
        };

        try (AvroParser p = factory.createParser(doc)) {
            AvroSchemaGenerator gen = new AvroSchemaGenerator();
            mapper.acceptJsonFormatVisitor(RootType.class, gen);
            p.setSchema(gen.getGeneratedSchema());
            p.getTextCharacters();
            p.nextToken();
            p.nextToken();
            p.nextToken();
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertTrue(e.getMessage().contains("Malformed UTF-8 character"));
        }
    }

    private class RootType {
        public String name;
        public int value;
    }
}
