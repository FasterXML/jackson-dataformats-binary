package com.fasterxml.jackson.dataformat.protobuf.dos;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.protobuf.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

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

    private final ProtobufMapper DEFAULT_MAPPER = newObjectMapper();
    private final ProtobufMapper MAPPER_UNLIMITED;
    {
        ProtobufFactory f = ProtobufFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        MAPPER_UNLIMITED = new ProtobufMapper(f);
    }

    private final ProtobufSchema NODE_SCHEMA;
    {
        try {
            NODE_SCHEMA = DEFAULT_MAPPER.generateSchemaFor(Node.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    
    public void testDeeplyNestedObjectsHighLimits() throws Exception
    {
        byte[] doc = genDeepDoc(1200);
        try (JsonParser p = protobufParser(MAPPER_UNLIMITED, doc)) {
            while (p.nextToken() != null) { }
        }
    }

    public void testDeeplyNestedObjectsLowLimits() throws Exception
    {
        byte[] doc = genDeepDoc(1200);
        try (JsonParser p = protobufParser(DEFAULT_MAPPER, doc)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertEquals("Depth (1001) exceeds the maximum allowed nesting depth (1000)",
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

    private JsonParser protobufParser(ObjectMapper mapper, byte[] doc) throws Exception {
        return mapper.readerFor(Node.class)
                .with(NODE_SCHEMA)
                .createParser(doc);
    }
}
