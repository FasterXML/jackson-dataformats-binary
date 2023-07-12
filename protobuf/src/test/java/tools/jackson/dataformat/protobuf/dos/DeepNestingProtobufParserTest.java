package tools.jackson.dataformat.protobuf.dos;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.protobuf.*;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

/**
 * Unit tests for deeply nested Documents
 */
public class DeepNestingProtobufParserTest extends ProtobufTestBase
{
    static class Node {
        public int id;
        public Node next;

        protected Node() { }
        public Node(int id, Node next) {
            this.id = id;
            this.next = next;
        }
    }

    private final static int NESTING_DEFAULT_MAX = StreamWriteConstraints.DEFAULT_MAX_DEPTH;
    private final static int NESTING_DEEPER = NESTING_DEFAULT_MAX + 100;
    
    private final ProtobufMapper DEFAULT_MAPPER = newObjectMapper();
    private final ProtobufMapper MAPPER_UNLIMITED;
    {
        ProtobufFactory f = ProtobufFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        MAPPER_UNLIMITED = new ProtobufMapper(f);
    }

    private final ProtobufSchema NODE_SCHEMA;
    {
        try {
            NODE_SCHEMA = DEFAULT_MAPPER.generateSchemaFor(Node.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void testDeeplyNestedObjectsHighLimits() throws Exception
    {
        byte[] doc = genDeepDoc(NESTING_DEEPER);
        try (JsonParser p = protobufParser(MAPPER_UNLIMITED, doc)) {
            while (p.nextToken() != null) { }
        }
    }

    public void testDeeplyNestedObjectsLowLimits() throws Exception
    {
        byte[] doc = genDeepDoc(NESTING_DEEPER);
        try (JsonParser p = protobufParser(DEFAULT_MAPPER, doc)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().startsWith("Document nesting depth ("+(NESTING_DEFAULT_MAX+1)+") exceeds the maximum allowed"));
        }
    }

    private byte[] genDeepDoc(int depth) throws Exception {
        Node node = null;

        while (--depth > 0) {
            node = new Node(depth, node);
        }
        return MAPPER_UNLIMITED.writer(NODE_SCHEMA)
                .writeValueAsBytes(node);
    }

    private JsonParser protobufParser(ObjectMapper mapper, byte[] doc) throws Exception {
        return mapper.readerFor(Node.class)
                .with(NODE_SCHEMA)
                .createParser(doc);
    }
}
