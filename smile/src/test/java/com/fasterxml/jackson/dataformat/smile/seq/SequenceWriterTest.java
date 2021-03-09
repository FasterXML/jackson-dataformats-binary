package com.fasterxml.jackson.dataformat.smile.seq;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class SequenceWriterTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    static class IdValue {
        public int id, value;

        public IdValue(int id, int value) {
            this.id = id;
            this.value = value;
        }
    }

    /*
    /**********************************************************
    /* Unit tests; happy case
    /**********************************************************
     */

    public void testSimpleSeqWrite() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (SequenceWriter seq = MAPPER.writer()
                .writeValues(bytes)) {
            seq.write(new IdValue(1, 15))
                .write(new IdValue(2, 16))
                .write(new IdValue(3, -999));
        }

        try (MappingIterator<JsonNode> it = MAPPER.readerFor(JsonNode.class)
                .readValues(bytes.toByteArray())) {
            assertTrue(it.hasNext());
            assertEquals(a2q("{'id':1,'value':15}"), it.nextValue().toString());
            assertTrue(it.hasNext());
            assertEquals(a2q("{'id':2,'value':16}"), it.nextValue().toString());
            assertTrue(it.hasNext());
            assertEquals(a2q("{'id':3,'value':-999}"), it.nextValue().toString());
            assertFalse(it.hasNext());
        }
    }
}
