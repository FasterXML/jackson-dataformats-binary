package com.fasterxml.jackson.dataformat.ion.fuzz;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#471]: AssertionError for corrupt Timestamp
// https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=66141
public class IonFuzz_471_66141_AssertionErrorTest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    @Test
    public void testFuzz66141_AssertionError() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-66141.ion");
        try {
            ION_MAPPER.readValue(doc, java.util.Date.class);
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt Number value to decode"));
        }
    }
}
