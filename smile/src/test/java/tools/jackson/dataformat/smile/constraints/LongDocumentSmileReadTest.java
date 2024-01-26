package tools.jackson.dataformat.smile.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.databind.SmileMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

public class LongDocumentSmileReadTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER_VANILLA = new SmileMapper();

    private final ObjectMapper MAPPER_CONSTRAINED = new SmileMapper(
            SmileFactory.builder()
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
                g.writeStringProperty("id", UUID.randomUUID().toString());
                g.writeNumberProperty("size", bytes.size());
                g.writeNumberProperty("stuff", Long.MAX_VALUE);
                g.writeEndObject();
                
                g.flush();
            } while (bytes.size() < size);
            g.writeEndArray();
        }
        return bytes.toByteArray();
    }
}
