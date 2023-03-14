package tools.jackson.dataformat.cbor.dos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORTestBase;

/**
 * Unit tests for deeply nested JSON
 */
public class DeepNestingCBORParserTest extends CBORTestBase
{
    public void testDeeplyNestedObjects() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        try (JsonParser jp = cborParser(out)) {
            JsonToken jt;
            while ((jt = jp.nextToken()) != null) {

            }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth (1001) exceeds the maximum allowed nesting depth (1000)", e.getMessage());
        }
    }

    public void testDeeplyNestedObjectsWithUnconstrainedMapper() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        CBORFactory cborFactory = CBORFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser jp = cborParser(cborFactory, out)) {
            JsonToken jt;
            while ((jt = jp.nextToken()) != null) {

            }
        }
    }

    public void testDeeplyNestedArrays() throws Exception
    {
        final int depth = 750;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        try (JsonParser jp = cborParser(out)) {
            JsonToken jt;
            while ((jt = jp.nextToken()) != null) {

            }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth (1001) exceeds the maximum allowed nesting depth (1000)", e.getMessage());
        }
    }

    public void testDeeplyNestedArraysWithUnconstrainedMapper() throws Exception
    {
        final int depth = 750;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        CBORFactory cborFactory = CBORFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser jp = cborParser(cborFactory, out)) {
            JsonToken jt;
            while ((jt = jp.nextToken()) != null) {

            }
        }
    }

    private void genDeepDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        try (JsonGenerator gen = cborGenerator(out)) {
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

    private void genDeepArrayDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        try (JsonGenerator gen = cborGenerator(out)) {
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
