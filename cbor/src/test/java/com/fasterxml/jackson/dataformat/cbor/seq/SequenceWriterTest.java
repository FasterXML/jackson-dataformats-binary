package com.fasterxml.jackson.dataformat.cbor.seq;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class SequenceWriterTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    @JsonPropertyOrder({ "id", "value"})
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

    @Test
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
