package com.fasterxml.jackson.dataformat.avro.dos;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

/**
 * Simple unit tests to verify that we fail gracefully if you attempt to serialize
 * data that is cyclic (eg a list that contains itself).
 */
public class CyclicAvroDataSerTest extends AvroTestBase
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

    // Unlike default depth of 1000 for other formats, use lower (400) here
    // because we cannot actually generate 1000 levels due to Avro codec's
    // limitations
    private final AvroMapper MAPPER_400;
    {
        AvroFactory f = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(400).build())
                .build();
        MAPPER_400 = new AvroMapper(f);
    }

    public void testDirectCyclic() throws Exception {
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

    // With 2.16 also test looser loops, wrt new limits
    public void testLooserCyclic() throws Exception
    {
        Bean beanRoot = new Bean(null, "123");
        Bean bean2 = new Bean(beanRoot, "456");
        beanRoot.assignNext(bean2);

        // 12-Jul-2023, tatu: Alas, won't work -- Avro serialization by-passes many
        //    checks. Needs more work in future

        if (false) {
            try {
                AvroSchema schema = MAPPER_400.schemaFor(Bean.class);
                MAPPER_400.writer(schema).writeValueAsBytes(beanRoot);
                fail("expected InvalidDefinitionException");
            } catch (InvalidDefinitionException idex) {
                assertTrue("InvalidDefinitionException message is as expected?",
                        idex.getMessage().startsWith("Direct self-reference leading to cycle"));
            }
        }
    }
}
