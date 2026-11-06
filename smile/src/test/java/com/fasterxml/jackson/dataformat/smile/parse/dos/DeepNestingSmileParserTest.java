package com.fasterxml.jackson.dataformat.smile.parse.dos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.async.AsyncReaderWrapper;
import com.fasterxml.jackson.dataformat.smile.async.AsyncReaderWrapperForByteArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for deeply nested JSON
 */
public class DeepNestingSmileParserTest extends BaseTestForSmile
{
    @Test
    public void testDeeplyNestedObjects() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        try (JsonParser p = _smileParser(out.toByteArray())) {
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
        SmileFactory smileFactory = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser p = _smileParser(smileFactory, out.toByteArray())) {
            while (p.nextToken() != null) {
                ;
            }
        }
    }

    @Test
    public void testDeeplyNestedArrays() throws Exception
    {
        final int depth = 750;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        try (JsonParser p = _smileParser(out.toByteArray())) {
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
        final int depth = 750;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        SmileFactory smileFactory = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser p = _smileParser(smileFactory, out.toByteArray())) {
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
        _readAllAsync(_unconstrainedFactory(), out.toByteArray());
    }

    @Test
    public void testDeeplyNestedArraysAsync() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, 750);
        _verifyAsyncDepthFailure(out.toByteArray());
    }

    @Test
    public void testDeeplyNestedArraysAsyncWithUnconstrainedMapper() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, 750);
        _readAllAsync(_unconstrainedFactory(), out.toByteArray());
    }

    private void _verifyAsyncDepthFailure(byte[] doc) throws IOException
    {
        try {
            _readAllAsync(new SmileFactory(), doc);
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue(e.getMessage().startsWith(exceptionPrefix),
                    "StreamConstraintsException message is as expected?");
        }
    }

    private void _readAllAsync(SmileFactory f, byte[] doc) throws IOException
    {
        AsyncReaderWrapper p = new AsyncReaderWrapperForByteArray(
                f.createNonBlockingByteArrayParser(), 1000, doc, 0);
        try {
            while (p.nextToken() != null) {
                ;
            }
        } finally {
            p.close();
        }
    }

    private SmileFactory _unconstrainedFactory() {
        return SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
    }

    private void genDeepDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        SmileFactory smileFactory = SmileFactory.builder()
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonGenerator gen = smileGenerator(smileFactory, out, true)) {
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

    private void genDeepArrayDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        SmileFactory smileFactory = SmileFactory.builder()
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonGenerator gen = smileGenerator(smileFactory, out, true)) {
            for (int i = 0; i < depth; i++) {
                gen.writeStartObject();
                gen.writeFieldName("a");
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
