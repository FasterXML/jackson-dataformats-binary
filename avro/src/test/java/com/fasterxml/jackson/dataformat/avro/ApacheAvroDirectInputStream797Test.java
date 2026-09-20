package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ApacheAvroDirectInputStream797Test extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    private final AvroMapper DIRECT_MAPPER = directApacheMapper();

    @Test
    public void testDirectInputStreamReadsRootValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("int"));
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(Integer.valueOf(123));

        try (JsonParser p = DIRECT_MAPPER.getFactory()
                .createParser(new ByteArrayInputStream(doc))) {
            p.setSchema(schema);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(123, p.getIntValue());
            assertNull(p.nextToken());
        }
    }

    @Test
    public void testDirectInputStreamTruncatedInputFails() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("int"));

        try (JsonParser p = DIRECT_MAPPER.getFactory()
                .createParser(new ByteArrayInputStream(new byte[] { (byte) 0x80 }))) {
            p.setSchema(schema);
            assertThrows(IOException.class, () -> p.nextToken());
        }
    }

    @Test
    public void testDirectInputStreamRootSequenceEOF() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("int"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (SequenceWriter sw = MAPPER.writer(schema).writeValues(out)) {
            sw.write(Integer.valueOf(1));
            sw.write(Integer.valueOf(123456));
            sw.write(Integer.valueOf(-999));
        }

        byte[] doc = out.toByteArray();
        AvroMapper mapper = directApacheMapperWithMaxDocumentLength(doc.length);

        try (MappingIterator<Integer> it = mapper.readerFor(Integer.class)
                .with(schema)
                .readValues(new ByteArrayInputStream(doc))) {
            assertTrue(it.hasNextValue());
            assertEquals(Integer.valueOf(1), it.nextValue());
            assertTrue(it.hasNextValue());
            assertEquals(Integer.valueOf(123456), it.nextValue());
            assertTrue(it.hasNextValue());
            assertEquals(Integer.valueOf(-999), it.nextValue());
            assertFalse(it.hasNextValue());
        }
    }

    private static AvroMapper directApacheMapper()
    {
        ApacheAvroFactory f = new ApacheAvroFactory();
        f.disable(AvroParser.Feature.AVRO_BUFFERING);
        return AvroMapper.builder(f).build();
    }

    private static AvroMapper directApacheMapperWithMaxDocumentLength(long maxDocumentLength)
    {
        ApacheAvroFactory f = new ApacheAvroFactory();
        f.disable(AvroParser.Feature.AVRO_BUFFERING);
        f.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxDocumentLength(maxDocumentLength).build());
        return AvroMapper.builder(f).build();
    }
}
