package com.fasterxml.jackson.dataformat.cbor.gen.dos;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple unit tests to verify that we fail gracefully if you attempt to serialize
 * data that is cyclic (eg a list that contains itself).
 */
public class CyclicDataSerTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testListWithSelfReference() throws Exception {
        List<Object> list = new ArrayList<>();
        list.add(list);
        try {
            MAPPER.writeValueAsBytes(list);
            fail("expected JsonMappingException");
        } catch (JsonMappingException jmex) {
            assertTrue("JsonMappingException message is as expected?",
                    jmex.getMessage().startsWith("Document nesting depth (1001) exceeds the maximum allowed"));
        }
    }
}
