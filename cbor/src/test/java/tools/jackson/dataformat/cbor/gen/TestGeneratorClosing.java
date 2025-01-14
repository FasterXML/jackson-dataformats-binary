package tools.jackson.dataformat.cbor.gen;

import tools.jackson.core.*;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.cbor.*;
import tools.jackson.dataformat.cbor.testutil.ByteOutputStreamForTesting;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Set of basic unit tests that verify aspect of closing a
 * {@link JsonGenerator} instance. This includes both closing
 * of physical resources (target), and logical content
 * (json content tree)
 *<p>
 * Specifically, features
 * <code>JsonGenerator.Feature#AUTO_CLOSE_TARGET</code>
 * and
 * <code>JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT</code>
 * are tested.
 */
public class TestGeneratorClosing extends CBORTestBase
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
        TokenStreamFactory f = cborFactory();

        // Check the default settings
        assertTrue(f.isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET));
        // then change
        f = f.rebuild().disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .build();
        assertFalse(f.isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET));

        try (final ByteOutputStreamForTesting output = new ByteOutputStreamForTesting()) {
            JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), output);

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
        TokenStreamFactory f = newFactoryBuilder()
                .enable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .build();
        @SuppressWarnings("resource")
        ByteOutputStreamForTesting output = new ByteOutputStreamForTesting();
        JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), output);

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
        TokenStreamFactory f = newFactoryBuilder()
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .build();
        @SuppressWarnings("resource")
        ByteOutputStreamForTesting output = new ByteOutputStreamForTesting();
        JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), output, JsonEncoding.UTF8);

        assertFalse(output.isClosed());
        g.writeNumber(39);
        g.close();
        assertFalse(output.isClosed());
    }

    @SuppressWarnings("resource")
    @Test
    public void testAutoFlushOrNot() throws Exception
    {
        TokenStreamFactory f = cborFactory();

        ByteOutputStreamForTesting bytes = new ByteOutputStreamForTesting();
        JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), bytes, JsonEncoding.UTF8);
        g.writeStartArray();
        g.writeEndArray();
        assertEquals(0, bytes.flushCount);
        g.flush();
        assertEquals(1, bytes.flushCount);
        final int EXP_LENGTH = 2;
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
        g = f.createGenerator(ObjectWriteContext.empty(), bytes, JsonEncoding.UTF8);
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

    private CBORFactoryBuilder newFactoryBuilder() {
        return CBORFactory.builder();
    }
}
