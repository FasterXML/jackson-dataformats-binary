package tools.jackson.dataformat.avro.constraints;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#785]: `StreamReadConstraints.maxTokenCount` for Avro
public class TokenCountAvroReadTest extends AvroTestBase
{
    public static class Ints {
        public List<Integer> values = new ArrayList<>();
    }

    private final AvroMapper MAPPER = newMapper();

    private final AvroSchema SCHEMA = MAPPER.schemaFor(Ints.class);

    // Verify token count is tracked accurately
    @Test
    public void testTokenCountIsTracked() throws Exception
    {
        // {"values":[1,2,3]}: START_OBJECT, PROPERTY_NAME, START_ARRAY,
        // VALUE_NUMBER_INT x3, END_ARRAY, END_OBJECT = 8 tokens
        byte[] doc = createDoc(3);
        for (AvroMapper mapper : new AvroMapper[] {
                mapperWithMaxTokenCount(false, Long.MAX_VALUE),
                mapperWithMaxTokenCount(true, Long.MAX_VALUE) }) {
            try (JsonParser p = mapper.reader().with(SCHEMA).createParser(doc)) {
                assertEquals(0L, p.currentTokenCount());
                while (p.nextToken() != null) { }
                assertEquals(8L, p.currentTokenCount());
            }
        }
    }

    @Test
    public void testTokenCountLimit() throws Exception
    {
        // createDoc(100) produces 100 + 5 tokens
        byte[] doc = createDoc(100);
        for (boolean apache : new boolean[] { false, true }) {
            AvroMapper mapper = mapperWithMaxTokenCount(apache, 10);
            _testTokenCountLimit(mapper.reader().with(SCHEMA).createParser(doc));
            _testTokenCountLimit(mapper.reader().with(SCHEMA).createParser(new ByteArrayInputStream(doc)));
        }
    }

    private void _testTokenCountLimit(JsonParser p) throws Exception
    {
        try (p) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Token count");
            verifyException(e, "exceeds the maximum allowed (10,");
        }
    }

    private AvroMapper mapperWithMaxTokenCount(boolean apacheDecoder, long maxTokenCount) {
        AvroFactoryBuilder b = apacheDecoder
                ? AvroFactory.builderWithApacheDecoder() : AvroFactory.builder();
        return new AvroMapper(b
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxTokenCount(maxTokenCount).build())
                .build());
    }

    private byte[] createDoc(int numValues) throws Exception {
        Ints ints = new Ints();
        for (int i = 0; i < numValues; i++) {
            ints.values.add(i);
        }
        return MAPPER.writer(SCHEMA).writeValueAsBytes(ints);
    }
}
