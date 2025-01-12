package tools.jackson.dataformat.ion.fuzz;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

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
