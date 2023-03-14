package tools.jackson.dataformat.avro.dos;

import java.io.IOException;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.avro.AvroFactory;
import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.AvroTestBase;

/**
 * Unit tests for deeply nested Documents
 */
public class DeepNestingAvroParserTest extends AvroTestBase
{
    protected final String NODE_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Node\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"id\", \"type\": \"int\"},\n"
            +" {\"name\": \"next\", \"type\": [\"Node\",\"null\"]}\n"
            +"]}";

    static class Node {
        public int id;
        public Node next;

        protected Node() { }
        public Node(int id, Node next) {
            this.id = id;
            this.next = next;
        }
    }

    private final AvroMapper DEFAULT_MAPPER = newMapper();

    // Unlike default depth of 1000 for other formats, use lower (500) here
    // because we cannot actually generate 1000 levels due to Avro codec's
    // limitations
    private final AvroMapper MAPPER_500;
    {
        AvroFactory f = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(500).build())
                .build();
        MAPPER_500 = new AvroMapper(f);
    }

    private final AvroSchema NODE_SCHEMA;
    {
        try {
            NODE_SCHEMA = DEFAULT_MAPPER.schemaFrom(NODE_SCHEMA_JSON);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    
    public void testDeeplyNestedObjectsHighLimits() throws Exception
    {
        byte[] doc = genDeepDoc(550);
        try (JsonParser jp = avroParser(DEFAULT_MAPPER, doc)) {
            while (jp.nextToken() != null) { }
        }
    }

    public void testDeeplyNestedObjectsLowLimits() throws Exception
    {
        byte[] doc = genDeepDoc(550);
        try (JsonParser jp = avroParser(MAPPER_500, doc)) {
            while (jp.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth (501) exceeds the maximum allowed nesting depth (500)",
                    e.getMessage());
        }
    }

    private byte[] genDeepDoc(int depth) throws Exception {
        Node node = null;

        while (--depth > 0) {
            node = new Node(depth, node);
        }
        return DEFAULT_MAPPER.writer(NODE_SCHEMA)
                .writeValueAsBytes(node);
    }

    private JsonParser avroParser(ObjectMapper mapper, byte[] doc) throws Exception {
        return mapper.readerFor(Node.class)
                .with(NODE_SCHEMA)
                .createParser(doc);
    }
}
