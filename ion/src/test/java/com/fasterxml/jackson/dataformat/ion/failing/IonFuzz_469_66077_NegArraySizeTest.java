package com.fasterxml.jackson.dataformat.ion.failing;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.ion.*;
import com.fasterxml.jackson.dataformat.ion.fuzz.IonFuzzTestUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

// [dataformats-binary#469]: Similar to OSS-Fuzz#66077 (but not necessarily same)
public class IonFuzz_469_66077_NegArraySizeTest
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
