package tools.jackson.dataformat.avro.constraints;

import org.junit.jupiter.api.Test;

import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test to verify enforcement of {@code StreamWriteConstraints.maxNestingDepth}
 * when writing Avro content.
 */
public class DeeplyNestedAvroWriteTest extends AvroTestBase
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

    // Unlike default depth of 1000 for other formats, use lower (400) here
    // because we cannot actually generate 1000 levels due to Avro codec's
    // limitations
    private final AvroMapper MAPPER_400;
    {
        AvroFactory f = AvroFactory.builder()
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(400).build())
                .build();
        MAPPER_400 = new AvroMapper(f);
    }

    @Test
    public void testTooDeepCyclic() throws Exception
    {
        LinkedBean beanRoot = new LinkedBean(null, "123");
        LinkedBean bean2 = new LinkedBean(beanRoot, "456");
        beanRoot.assignNext(bean2);

        try {
            AvroSchema schema = MAPPER_400.schemaFor(LinkedBean.class);
            MAPPER_400.writer(schema).writeValueAsBytes(beanRoot);
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Document nesting depth");
        }
    }
}
