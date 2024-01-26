package tools.jackson.dataformat.smile.constraints;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.databind.SmileMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

public class DeeplyNestedSmileReadWriteTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER_VANILLA = smileMapper(false, true, false);

    private final ObjectMapper MAPPER_CONSTRAINED = new SmileMapper(
            SmileFactory.builder()
            // Use higher limit for writing to simplify testing setup
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(10).build())
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(12).build())
            .build()
            );

    public void testDeepNestingArrayRead() throws Exception {
        _testDeepNestingRead(createDeepNestedArrayDoc(13));
    }

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

    public void testDeepNestingArrayWrite() throws Exception {
        _testDeepNestingWrite(createDeepNestedArrayDoc(13));
    }

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
