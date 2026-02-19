package com.fasterxml.jackson.dataformat.cbor.gen.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LongDocumentCBORReadTest extends CBORTestBase
{
    private final CBORMapper MAPPER_VANILLA = cborMapper();

    private final CBORMapper MAPPER_CONSTRAINED = cborMapper(
            CBORFactory.builder()
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
