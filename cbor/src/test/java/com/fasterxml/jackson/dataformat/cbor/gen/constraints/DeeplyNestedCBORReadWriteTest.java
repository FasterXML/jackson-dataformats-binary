package com.fasterxml.jackson.dataformat.cbor.gen.constraints;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DeeplyNestedCBORReadWriteTest extends CBORTestBase
{
    private final ObjectMapper MAPPER_VANILLA = cborMapper();

    private final ObjectMapper MAPPER_CONSTRAINED = cborMapper(
            CBORFactory.builder()
            // Use higher limit for writing to simplify testing setup
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(10).build())
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(12).build())
            .build()
            );

    @Test
    public void testDeepNestingArrayRead() throws Exception {
        _testDeepNestingRead(createDeepNestedArrayDoc(13));
    }

    @Test
    public void testDeepNestingObjectRead() throws Exception {
        _testDeepNestingRead(createDeepNestedObjectDoc(13));
    }

    private void _testDeepNestingRead(JsonNode docRoot) throws Exception
    {
        byte[] doc = MAPPER_VANILLA.writeValueAsBytes(docRoot);
        try (JsonParser p = MAPPER_CONSTRAINED.createParser(doc)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertEquals("Document nesting depth (11) exceeds the maximum allowed (10, from `StreamReadConstraints.getMaxNestingDepth()`)",
                    e.getMessage());
        }
    }

    @Test
    public void testDeepNestingArrayWrite() throws Exception {
        _testDeepNestingWrite(createDeepNestedArrayDoc(13));
    }

    @Test
    public void testDeepNestingObjectWrite() throws Exception {
        _testDeepNestingWrite(createDeepNestedObjectDoc(13));
    }

    private void _testDeepNestingWrite(JsonNode docRoot) throws Exception
    {
        try {
            MAPPER_CONSTRAINED.writeValueAsBytes(docRoot);
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            assertEquals("Document nesting depth (13) exceeds the maximum allowed (12, from `StreamWriteConstraints.getMaxNestingDepth()`)",
                    e.getMessage());
        }
    }

    private JsonNode createDeepNestedArrayDoc(final int depth) throws Exception
    {
        final ArrayNode root = MAPPER_VANILLA.createArrayNode();
        ArrayNode curr = root;
        for (int i = 0; i < depth; ++i) {
            curr.add(42);
            curr = curr.addArray();
        }
        curr.add("text");
        return root;
    }

    private JsonNode createDeepNestedObjectDoc(final int depth) throws Exception
    {
        final ObjectNode root = MAPPER_VANILLA.createObjectNode();
        ObjectNode curr = root;
        for (int i = 0; i < depth; ++i) {
            curr = curr.putObject("nested"+i);
        }
        curr.put("value", 42);
        return root;
    }
}
