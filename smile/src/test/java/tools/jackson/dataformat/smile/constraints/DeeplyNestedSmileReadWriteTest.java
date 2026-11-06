package tools.jackson.dataformat.smile.constraints;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapper;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapperForByteArray;

import static org.junit.jupiter.api.Assertions.*;

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

    // Nesting exactly at the limit (root + 9 = 10 levels) must still be accepted
    @Test
    public void testMaxNestingArrayRead() throws Exception {
        _testMaxNestingRead(createDeepNestedArrayDoc(9));
    }

    @Test
    public void testMaxNestingObjectRead() throws Exception {
        _testMaxNestingRead(createDeepNestedObjectDoc(9));
    }

    private void _testMaxNestingRead(JsonNode docRoot) throws Exception
    {
        byte[] doc = MAPPER_VANILLA.writeValueAsBytes(docRoot);
        try (JsonParser p = MAPPER_CONSTRAINED.createParser(doc)) {
            int maxDepth = 0;
            while (p.nextToken() != null) {
                maxDepth = Math.max(maxDepth, p.streamReadContext().getNestingDepth());
            }
            assertEquals(10, maxDepth);
        }
    }

    @Test
    public void testDeepNestingArrayReadAsync() throws Exception {
        _testDeepNestingReadAsync(createDeepNestedArrayDoc(13));
    }

    @Test
    public void testDeepNestingObjectReadAsync() throws Exception {
        _testDeepNestingReadAsync(createDeepNestedObjectDoc(13));
    }

    private void _testDeepNestingReadAsync(JsonNode docRoot) throws Exception
    {
        byte[] doc = MAPPER_VANILLA.writeValueAsBytes(docRoot);
        for (int bytesPerFeed : new int[] { 1, 1000 }) {
            AsyncReaderWrapper p = _asyncParser(bytesPerFeed, doc);
            try {
                while (p.nextToken() != null) { }
                fail("expected StreamConstraintsException (bytesPerFeed: "+bytesPerFeed+")");
            } catch (StreamConstraintsException e) {
                assertEquals("Document nesting depth (11) exceeds the maximum allowed (10, from `StreamReadConstraints.getMaxNestingDepth()`)",
                        e.getMessage());
            } finally {
                p.close();
            }
        }
    }

    // Nesting exactly at the limit (root + 9 = 10 levels) must still be accepted
    @Test
    public void testMaxNestingArrayReadAsync() throws Exception {
        _testMaxNestingReadAsync(createDeepNestedArrayDoc(9));
    }

    @Test
    public void testMaxNestingObjectReadAsync() throws Exception {
        _testMaxNestingReadAsync(createDeepNestedObjectDoc(9));
    }

    private void _testMaxNestingReadAsync(JsonNode docRoot) throws Exception
    {
        byte[] doc = MAPPER_VANILLA.writeValueAsBytes(docRoot);
        for (int bytesPerFeed : new int[] { 1, 1000 }) {
            AsyncReaderWrapper p = _asyncParser(bytesPerFeed, doc);
            try {
                int maxDepth = 0;
                while (p.nextToken() != null) {
                    maxDepth = Math.max(maxDepth, p.parser().streamReadContext().getNestingDepth());
                }
                assertEquals(10, maxDepth, "bytesPerFeed: "+bytesPerFeed);
            } finally {
                p.close();
            }
        }
    }

    private AsyncReaderWrapper _asyncParser(int bytesPerFeed, byte[] doc) {
        return new AsyncReaderWrapperForByteArray(
                MAPPER_CONSTRAINED.reader().createNonBlockingByteArrayParser(),
                bytesPerFeed, doc, 0);
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
