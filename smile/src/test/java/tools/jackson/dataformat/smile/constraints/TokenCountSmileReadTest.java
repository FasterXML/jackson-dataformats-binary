package tools.jackson.dataformat.smile.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#651] Validate maxTokenCount support for Smile
public class TokenCountSmileReadTest extends BaseTestForSmile
{
    private final SmileMapper MAPPER = new SmileMapper();

    // Verify token count is tracked accurately
    @Test
    public void testTokenCountIsTracked() throws Exception
    {
        // [1, 2, 3]: START_ARRAY, VALUE_NUMBER_INT x3, END_ARRAY = 5 tokens
        byte[] doc = createDoc(3);
        SmileMapper mapper = mapperWithMaxTokenCount(Long.MAX_VALUE);
        try (JsonParser p = mapper.createParser(doc)) {
            assertEquals(0L, p.currentTokenCount());
            while (p.nextToken() != null) { }
            assertEquals(5L, p.currentTokenCount());
        }
    }

    @Test
    public void testTokenCountLimitWithStream() throws Exception
    {
        // createDoc(100) produces START_ARRAY + 100xVALUE_NUMBER_INT + END_ARRAY = 102 tokens
        byte[] doc = createDoc(100);
        SmileMapper mapper = mapperWithMaxTokenCount(10);
        try (JsonParser p = mapper.createParser(new ByteArrayInputStream(doc))) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Token count");
            verifyException(e, "exceeds the maximum allowed (10,");
        }
    }

    @Test
    public void testTokenCountLimitWithByteArray() throws Exception
    {
        // createDoc(100) produces START_ARRAY + 100xVALUE_NUMBER_INT + END_ARRAY = 102 tokens
        byte[] doc = createDoc(100);
        SmileMapper mapper = mapperWithMaxTokenCount(10);
        try (JsonParser p = mapper.createParser(doc)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Token count");
            verifyException(e, "exceeds the maximum allowed (10,");
        }
    }

    private SmileMapper mapperWithMaxTokenCount(long maxTokenCount) {
        return SmileMapper.builder(
            SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxTokenCount(maxTokenCount).build())
                .build())
            .build();
    }

    // Creates a Smile-encoded array document with the given number of integer elements.
    // Token count = numValues + 2 (START_ARRAY + END_ARRAY)
    private byte[] createDoc(int numValues) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator g = MAPPER.createGenerator(out)) {
            g.writeStartArray();
            for (int i = 0; i < numValues; i++) {
                g.writeNumber(i);
            }
            g.writeEndArray();
        }
        return out.toByteArray();
    }
}
