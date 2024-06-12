package tools.jackson.dataformat.smile.gen.dos;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.*;

import tools.jackson.dataformat.smile.BaseTestForSmile;

/**
 * Simple unit tests to verify that we fail gracefully if you attempt to serialize
 * data that is cyclic (eg a list that contains itself).
 */
public class CyclicSmileDataSerTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    public void testListWithSelfReference() throws Exception {
        List<Object> list = new ArrayList<>();
        list.add(list);
        try {
            MAPPER.writeValueAsBytes(list);
            fail("expected JsonMappingException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamWriteConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue("DatabindException message is as expected?",
                    e.getMessage().startsWith(exceptionPrefix));
        }
    }
}
