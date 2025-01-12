package com.fasterxml.jackson.dataformat.smile.gen;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.testutil.ByteOutputStreamForTesting;

import static org.junit.jupiter.api.Assertions.*;

public class TestGeneratorClosing extends BaseTestForSmile
{
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    /**
     * This unit test checks the default behaviour; with no auto-close, no
     * automatic closing should occur, nor explicit one unless specific
     * forcing method is used.
     */
    @Test
    public void testNoAutoCloseGenerator() throws Exception
    {
        JsonFactory f = newFactory();

        // Check the default settings
        assertTrue(f.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
        // then change
        f.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertFalse(f.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        try (final ByteOutputStreamForTesting output = new ByteOutputStreamForTesting()) {
            JsonGenerator g = f.createGenerator(output);

            // shouldn't be closed to begin with...
            assertFalse(output.isClosed());
            g.writeNumber(39);
            // regular close won't close it either:
            g.close();
            assertFalse(output.isClosed());
        }
    }

    @Test
    public void testCloseGenerator() throws Exception
    {
        JsonFactory f = newFactory();
        f.enable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        @SuppressWarnings("resource")
        ByteOutputStreamForTesting output = new ByteOutputStreamForTesting();
        JsonGenerator g = f.createGenerator(output);

        // shouldn't be closed to begin with...
        assertFalse(output.isClosed());
        g.writeNumber(39);
        // but close() should now close the writer
        g.close();
        assertTrue(output.isClosed());
    }

    @Test
    public void testNoAutoCloseOutputStream() throws Exception
    {
        JsonFactory f = newFactory();
        f.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        @SuppressWarnings("resource")
        ByteOutputStreamForTesting output = new ByteOutputStreamForTesting();
        JsonGenerator g = f.createGenerator(output, JsonEncoding.UTF8);

        assertFalse(output.isClosed());
        g.writeNumber(39);
        g.close();
        assertFalse(output.isClosed());
    }

    @SuppressWarnings("resource")
    @Test
    public void testAutoFlushOrNot() throws Exception
    {
        JsonFactory f = newFactory();

        ByteOutputStreamForTesting bytes = new ByteOutputStreamForTesting();
        JsonGenerator g = f.createGenerator(bytes, JsonEncoding.UTF8);
        g.writeStartArray();
        g.writeEndArray();
        assertEquals(0, bytes.flushCount);
        g.flush();
        assertEquals(1, bytes.flushCount);
        final int EXP_LENGTH = 6;
        assertEquals(EXP_LENGTH, bytes.toByteArray().length);
        g.close();

        // then disable and we should not see flushing again...
        f = newFactoryBuilder()
                .disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
                // also need to disable this, to prevent implicit flush() on close()
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .build();

        // and then with OutputStream
        bytes = new ByteOutputStreamForTesting();
        g = f.createGenerator(bytes, JsonEncoding.UTF8);
        g.writeStartArray();
        g.writeEndArray();
        assertEquals(0, bytes.flushCount);
        g.flush();
        assertEquals(0, bytes.flushCount);
        // and, as long as we won't be auto-closing, still no flush
        g.close();
        assertEquals(0, bytes.flushCount);
        // and only direct `close()` will do it
        bytes.close();
        assertEquals(EXP_LENGTH, bytes.toByteArray().length);
    }

    private TSFBuilder<?, ?> newFactoryBuilder() {
        return SmileFactory.builder();
    }

    private SmileFactory newFactory() {
        return new SmileFactory();
    }
}
