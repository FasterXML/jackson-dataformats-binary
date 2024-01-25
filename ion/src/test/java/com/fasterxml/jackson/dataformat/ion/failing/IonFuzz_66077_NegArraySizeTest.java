package com.fasterxml.jackson.dataformat.ion.failing;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.ion.*;
import com.fasterxml.jackson.dataformat.ion.fuzz.IonFuzzTestUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

// Similar to OSS-Fuzz#66077 (but not same)
public class IonFuzz_66077_NegArraySizeTest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    @Test
    public void testFuzz66077_NegativeArraySize() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-66077.ion");
        try {
            ION_MAPPER.readTree(doc);
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            // May or may not be the exception message to get, change as appropriate
            assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
        }
    }
}
