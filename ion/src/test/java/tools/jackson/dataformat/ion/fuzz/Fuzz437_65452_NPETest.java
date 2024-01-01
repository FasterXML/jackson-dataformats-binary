package tools.jackson.dataformat.ion.fuzz;

import java.io.*;

import org.hamcrest.Matchers;
import org.junit.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.ion.*;

import tools.jackson.dataformat.ion.fuzz.IonFuzzTestUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

// [dataformats-binary#437]
public class Fuzz437_65452_NPETest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    @Test
    public void testFuzz65452NPE() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-65452.ion");
        try (InputStream in = new ByteArrayInputStream(doc)) {
            try (JsonParser p = ION_MAPPER.createParser(in)) {
                p.nextToken();
            }
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
        }
    }
}
