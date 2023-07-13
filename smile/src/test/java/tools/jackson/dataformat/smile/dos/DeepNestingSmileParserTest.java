package tools.jackson.dataformat.smile.dos;

import java.io.ByteArrayOutputStream;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.databind.SmileMapper;

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
            assertTrue("StreamConstraintsException message is as expected?",
                    e.getMessage().startsWith(exceptionPrefix));
        }
    }

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
            assertTrue("StreamConstraintsException message is as expected?",
                    e.getMessage().startsWith(exceptionPrefix));
        }
    }

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
