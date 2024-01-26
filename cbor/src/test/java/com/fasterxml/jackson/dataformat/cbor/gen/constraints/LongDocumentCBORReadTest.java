package com.fasterxml.jackson.dataformat.cbor.gen.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class LongDocumentCBORReadTest extends CBORTestBase
{
    private final ObjectMapper MAPPER_VANILLA = cborMapper();

    private final ObjectMapper MAPPER_CONSTRAINED = cborMapper(
            CBORFactory.builder()
            // limit to 100kB doc reads
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(50_000)
            .build()
            ).build());

    public void testLongDocumentConstraint() throws Exception
    {
        // Need a bit longer than minimum since checking is approximate, not exact
        byte[] doc = createBigDoc(60_000);
        // Must read from `InputStream` as validation is during "loadMore()":
        try (JsonParser p = MAPPER_CONSTRAINED.createParser(new ByteArrayInputStream(doc))) {
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
