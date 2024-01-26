package tools.jackson.dataformat.smile.constraints;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.databind.SmileMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

public class DeeplyNestedSmileReadWriteTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER_VANILLA = smileMapper();

    private final ObjectMapper MAPPER_CONSTRAINED = new SmileMapper(
            SmileFactory.builder()
            // Use higher limit for writing to simplify testing setup
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(10).build())
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(12).build())
            .build()
            );

    public void testDeepNestingRead() throws Exception
    {
        final byte[] DOC = MAPPER_CONSTRAINED.writeValueAsBytes(createDeepNestedDoc(11));
        try (JsonParser p = MAPPER_CONSTRAINED.createParser(DOC)) {
            _testDeepNestingRead(p);
        }
    }

    private void _testDeepNestingRead(JsonParser p) throws Exception
    {
        try {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            assertEquals("Document nesting depth (11) exceeds the maximum allowed (10, from `StreamReadConstraints.getMaxNestingDepth()`)",
                    e.getMessage());
        }
    }

    public void testDeepNestingWrite() throws Exception
    {
        final JsonNode docRoot = createDeepNestedDoc(13);
        try {
            MAPPER_CONSTRAINED.writeValueAsBytes(docRoot);
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            assertEquals("Document nesting depth (13) exceeds the maximum allowed (12, from `StreamWriteConstraints.getMaxNestingDepth()`)",
                    e.getMessage());
        }
    }

    private JsonNode createDeepNestedDoc(final int depth) throws Exception
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
