package com.fasterxml.jackson.dataformat.ion.sequence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class MappingIteratorTest {

    private static final ObjectMapper MAPPER = new IonObjectMapper();

    @Test
    public void testReadFromWrite() throws Exception {
        final Object[] values = new String[]{"1", "2", "3", "4"};

        // write
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SequenceWriter seq = MAPPER.writer().writeValues(out)) {
            for (Object value : values) {
                seq.write(value);
            }
        }

        // read
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        try (MappingIterator<Object> it = MAPPER.readerFor(Object.class).readValues(in)) {
            for (Object value : values) {
                assertTrue(it.hasNext());
                assertTrue(it.hasNext()); // should not alter the iterator state
                assertEquals(value, it.next());
            }
            assertFalse(it.hasNext());
        }
    }

    @Test
    public void testReadFromEmpty() throws Exception {
        final ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        try (MappingIterator<Object> it = MAPPER.readerFor(Object.class).readValues(in)) {
            assertFalse(it.hasNext());
        }
    }
}
