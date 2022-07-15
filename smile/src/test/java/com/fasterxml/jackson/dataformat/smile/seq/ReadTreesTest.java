package com.fasterxml.jackson.dataformat.smile.seq;

import java.util.List;

import tools.jackson.databind.*;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class ReadTreesTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    static class IdValue {
        public int id, value;
    }

    /*
    /**********************************************************
    /* Unit tests; happy case
    /**********************************************************
     */

    public void testReadTreeSequence() throws Exception {
        _testReadTreeSequence(true);
        _testReadTreeSequence(false);
    }
    
    public void _testReadTreeSequence(boolean writeHeader) throws Exception
    {
        final byte[] INPUT = concat(
                _smileDoc(a2q("{\"id\":1, \"value\":137 }"), writeHeader),
                _smileDoc(a2q("{\"id\":2, \"value\":256 }"), writeHeader),
                _smileDoc(a2q("{\"id\":3, \"value\":-89 }"), writeHeader)
        );
//System.err.println("DEBUG/Smile: byte length = "+INPUT.length);

        try (MappingIterator<JsonNode> it = MAPPER.readerFor(JsonNode.class)
                .readValues(INPUT)) {
            assertTrue(it.hasNextValue());
            JsonNode node = it.nextValue();
            assertEquals("{\"id\":1,\"value\":137}", node.toString());
            assertEquals(1, node.path("id").intValue());
            assertEquals(1, node.path("id").asInt());

            assertTrue(it.hasNextValue());
            node = it.nextValue();
            assertEquals("{\"id\":2,\"value\":256}", node.toString());

            assertTrue(it.hasNextValue());
            node = it.nextValue();
            assertEquals("{\"id\":3,\"value\":-89}", node.toString());

            assertFalse(it.hasNextValue());
        }

        // Or with "readAll()":
        try (MappingIterator<JsonNode> it = MAPPER.readerFor(JsonNode.class)
                .readValues(INPUT)) {
            List<JsonNode> all = it.readAll();
            assertEquals(3, all.size());
            assertEquals("{\"id\":3,\"value\":-89}", all.get(2).toString());
        }
    }

    /*
    /**********************************************************
    /* (note: no error recovery unlike in JSON tests, should
    /* not differ)
    /**********************************************************
     */
}
