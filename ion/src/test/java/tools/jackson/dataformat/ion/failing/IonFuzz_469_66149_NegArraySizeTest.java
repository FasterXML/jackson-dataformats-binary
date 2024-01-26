package tools.jackson.dataformat.ion.failing;

import org.hamcrest.Matchers;
import org.junit.Test;

import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.ion.*;
import tools.jackson.dataformat.ion.fuzz.IonFuzzTestUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

// [dataformats-binary#469]: NegativeArraySizeException
// https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=66141
public class IonFuzz_469_66149_NegArraySizeTest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    @Test
    public void testFuzz66149_NegativeArraySize() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-66149.ion");
        try {
            ION_MAPPER.readTree(doc);
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            // May or may not be the exception message to get, change as appropriate
            assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
        }
    }
}
