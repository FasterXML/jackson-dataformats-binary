package tools.jackson.dataformat.avro.dos;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.exc.InvalidDefinitionException;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Simple unit test to verify that we fail gracefully if you attempt to serialize
 * data that is directly cyclic (eg a list that contains itself).
 */
public class CyclicAvroDataSerTest extends AvroTestBase
{
    public static class LinkedBean
    {
        LinkedBean _next;
        final String _name;

        public LinkedBean(LinkedBean next, String name) {
            _next = next;
            _name = name;
        }

        public LinkedBean getNext() { return _next; }
        public String getName() { return _name; }

        public void assignNext(LinkedBean n) { _next = n; }
    }

    private final AvroMapper MAPPER = getMapper();

    @Test
    public void testDirectCyclic() throws Exception {
        LinkedBean bean = new LinkedBean(null, "123");
        bean.assignNext(bean);
        try {
            AvroSchema schema = MAPPER.schemaFor(LinkedBean.class);
            MAPPER.writer(schema).writeValueAsBytes(bean);
            fail("expected InvalidDefinitionException");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Direct self-reference leading to cycle");
        }
    }
}
