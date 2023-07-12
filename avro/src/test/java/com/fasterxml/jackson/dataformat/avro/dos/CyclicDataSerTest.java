package com.fasterxml.jackson.dataformat.avro.dos;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

/**
 * Simple unit tests to verify that we fail gracefully if you attempt to serialize
 * data that is cyclic (eg a list that contains itself).
 */
public class CyclicDataSerTest extends AvroTestBase
{

    public static class Bean
    {
        Bean _next;
        final String _name;

        public Bean(Bean next, String name) {
            _next = next;
            _name = name;
        }

        public Bean getNext() { return _next; }
        public String getName() { return _name; }

        public void assignNext(Bean n) { _next = n; }
    }

    private final AvroMapper MAPPER = getMapper();

    public void testCyclic() throws Exception {
        Bean bean = new Bean(null, "123");
        bean.assignNext(bean);
        try {
            AvroSchema schema = MAPPER.schemaFor(Bean.class);
            MAPPER.writer(schema).writeValueAsBytes(bean);
            fail("expected InvalidDefinitionException");
        } catch (InvalidDefinitionException idex) {
            assertTrue("InvalidDefinitionException message is as expected?",
                    idex.getMessage().startsWith("Direct self-reference leading to cycle"));
        }
    }
}
