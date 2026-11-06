package tools.jackson.dataformat.smile.dos;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapper;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapperForByteArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for deeply nested JSON
 */
public class DeepNestingSmileParserTest extends BaseTestForSmile
{
    SmileMapper DEFAULT_MAPPER = newSmileMapper();

    SmileMapper UNCONSTRAINED_MAPPER;
    {
        SmileFactory smileFactory = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        UNCONSTRAINED_MAPPER = new SmileMapper(smileFactory);
    }

    @Test
    public void testDeeplyNestedObjects() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        try (JsonParser p = DEFAULT_MAPPER.createParser(out.toByteArray())) {
            while (p.nextToken() != null) {
                ;
            }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue(e.getMessage().startsWith(exceptionPrefix),
                    "StreamConstraintsException message is as expected?");
        }
    }

    @Test
    public void testDeeplyNestedObjectsWithUnconstrainedMapper() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        try (JsonParser p = UNCONSTRAINED_MAPPER.createParser(out.toByteArray())) {
            while (p.nextToken() != null) {
                ;
            }
        }
    }

    @Test
    public void testDeeplyNestedArrays() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        try (JsonParser p = DEFAULT_MAPPER.createParser(out.toByteArray())) {
            while (p.nextToken() != null) {
                ;
            }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue(e.getMessage().startsWith(exceptionPrefix),
                    "StreamConstraintsException message is as expected?");
        }
    }

    @Test
    public void testDeeplyNestedArraysWithUnconstrainedMapper() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        try (JsonParser p = UNCONSTRAINED_MAPPER.createParser(out.toByteArray())) {
            while (p.nextToken() != null) {
                ;
            }
        }
    }

    // Non-blocking parser must enforce the same default limit

    @Test
    public void testDeeplyNestedObjectsAsync() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, 1500);
        _verifyAsyncDepthFailure(out.toByteArray());
    }

    @Test
    public void testDeeplyNestedObjectsAsyncWithUnconstrainedMapper() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, 1500);
        _readAllAsync(UNCONSTRAINED_MAPPER, out.toByteArray());
    }

    @Test
    public void testDeeplyNestedArraysAsync() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, 1500);
        _verifyAsyncDepthFailure(out.toByteArray());
    }

    @Test
    public void testDeeplyNestedArraysAsyncWithUnconstrainedMapper() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, 1500);
        _readAllAsync(UNCONSTRAINED_MAPPER, out.toByteArray());
    }

    private void _verifyAsyncDepthFailure(byte[] doc)
    {
        try {
            _readAllAsync(DEFAULT_MAPPER, doc);
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue(e.getMessage().startsWith(exceptionPrefix),
                    "StreamConstraintsException message is as expected?");
        }
    }

    private void _readAllAsync(SmileMapper mapper, byte[] doc)
    {
        AsyncReaderWrapper p = new AsyncReaderWrapperForByteArray(
                mapper.reader().createNonBlockingByteArrayParser(), 1000, doc, 0);
        try {
            while (p.nextToken() != null) {
                ;
            }
        } finally {
            p.close();
        }
    }

    private void genDeepDoc(final ByteArrayOutputStream out, final int depth)
    {
        try (JsonGenerator gen = UNCONSTRAINED_MAPPER.createGenerator(out)) {
            for (int i = 0; i < depth; i++) {
                gen.writeStartObject();
                gen.writeName("a");
            }
            gen.writeString("val");
            for (int i = 0; i < depth; i++) {
                gen.writeEndObject();
            }
        }
    }

    private void genDeepArrayDoc(final ByteArrayOutputStream out, final int depth)
    {
        try (JsonGenerator gen = UNCONSTRAINED_MAPPER.createGenerator(out)) {
            for (int i = 0; i < depth; i++) {
                gen.writeStartObject();
                gen.writeName("a");
                gen.writeStartArray();
            }
            gen.writeString("val");
            for (int i = 0; i < depth; i++) {
                gen.writeEndArray();
                gen.writeEndObject();
            }
        }
    }
}
