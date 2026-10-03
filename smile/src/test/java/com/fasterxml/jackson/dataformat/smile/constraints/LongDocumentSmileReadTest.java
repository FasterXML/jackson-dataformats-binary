package com.fasterxml.jackson.dataformat.smile.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LongDocumentSmileReadTest extends BaseTestForSmile
{
    private final SmileMapper MAPPER_VANILLA = new SmileMapper();

    private final SmileMapper MAPPER_CONSTRAINED = new SmileMapper(
            SmileFactory.builder()
            // limit to 100kB doc reads
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(50_000)
            .build()
            ).build());

    @Test
    public void testLongDocumentConstraint() throws Exception
    {
        // Need a bit longer than minimum since checking is approximate, not exact
        byte[] doc = createBigDoc(60_000);
        _testLongDocumentConstraint(doc, true);
        // [dataformats-binary#649] fixed buffer too
        _testLongDocumentConstraint(doc, false);
    }

    private void _testLongDocumentConstraint(byte[] doc, boolean stream) throws Exception
    {
        try (JsonParser p = stream
                ? MAPPER_CONSTRAINED.createParser(new ByteArrayInputStream(doc))
                : MAPPER_CONSTRAINED.createParser(doc, 0, doc.length)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            final String msg = e.getMessage();
            assertTrue(msg.contains("Document length ("));
            assertTrue(msg.contains("exceeds the maximum allowed (50000"));
        }
    }
    
    // Non-blocking: content being fed should count too, not just content
    // fed earlier
    @Test
    public void testLongDocumentConstraintAsyncSingleFeed() throws Exception
    {
        byte[] doc = createBigDoc(60_000);
        try (JsonParser p = MAPPER_CONSTRAINED.getFactory().createNonBlockingByteArrayParser()) {
            ((ByteArrayFeeder) p.getNonBlockingInputFeeder()).feedInput(doc, 0, doc.length);
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            final String msg = e.getMessage();
            assertTrue(msg.contains("Document length ("+doc.length+")"), msg);
            assertTrue(msg.contains("exceeds the maximum allowed (50000"), msg);
        }
    }

    // Non-blocking: rejected feed should leave parser state untouched
    @Test
    public void testLongDocumentConstraintAsyncRejectedFeed() throws Exception
    {
        byte[] doc = createBigDoc(60_000);
        try (JsonParser p = MAPPER_CONSTRAINED.getFactory().createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            feeder.feedInput(doc, 0, 30_000);
            while (p.nextToken() != JsonToken.NOT_AVAILABLE) { }
            final long offset = p.currentLocation().getByteOffset();
            try {
                feeder.feedInput(doc, 30_000, 60_000);
                fail("expected StreamConstraintsException");
            } catch (StreamConstraintsException e) {
                assertTrue(e.getMessage().contains("Document length (60000)"), e.getMessage());
            }
            assertEquals(offset, p.currentLocation().getByteOffset());
            // but smaller chunk is fine: total of 45000
            feeder.feedInput(doc, 30_000, 45_000);
            JsonToken t = p.nextToken();
            assertTrue((t != null) && (t != JsonToken.NOT_AVAILABLE), "Unexpected token: "+t);
        }
    }

    private byte[] createBigDoc(final int size) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(size + 1000);
        try (JsonGenerator g = MAPPER_VANILLA.createGenerator(bytes)) {
            g.writeStartArray();

            do {
                g.writeStartObject();
                g.writeStringField("id", UUID.randomUUID().toString());
                g.writeNumberField("size", bytes.size());
                g.writeNumberField("stuff", Long.MAX_VALUE);
                g.writeEndObject();
                
                g.flush();
            } while (bytes.size() < size);
            g.writeEndArray();
        }
        return bytes.toByteArray();
    }
}
