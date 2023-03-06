package com.fasterxml.jackson.dataformat.cbor.parse.dos;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.dataformat.cbor.CBORConstants;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.testutil.ThrottledInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Unit tests for deeply nested JSON
 */
public class DeepNestingParserTest extends CBORTestBase
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

    private void genDeepDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        try (JsonGenerator gen = cborGenerator(out)) {
            for (int i = 0; i < depth; i++) {
                gen.writeStartObject();
                gen.writeFieldName("a");
            }
            gen.writeString("val");
            for (int i = 0; i < depth; i++) {
                gen.writeEndObject();
            }
        }
    }
}
