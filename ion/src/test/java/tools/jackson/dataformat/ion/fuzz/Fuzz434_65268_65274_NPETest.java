package tools.jackson.dataformat.ion.fuzz;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#434]
public class Fuzz434_65268_65274_NPETest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    // Test that used to fail on "getNumberType()" for `JsonToken.VALUE_NULL`
    @Test
    public void testFuzz65268() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-65268.ion");
        try (InputStream in = new ByteArrayInputStream(doc)) {
           try (JsonParser p = ION_MAPPER.createParser(in)) {
               assertEquals(JsonToken.VALUE_STRING, p.nextToken());
               p.getString();
               assertNull(p.nextStringValue());
               assertEquals(JsonToken.VALUE_NULL, p.currentToken());
               assertNull(p.getNumberType());
           }
       }
    }

    @Test
    public void testFuzz65274Malformed() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-65274.ion");
        try {
            ION_MAPPER.readTree(new ByteArrayInputStream(doc));
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt Number value to decode"));
        }
    }

    @Test
    public void testFuzz65274Eof() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65274.ion")) {
            ION_MAPPER.readTree(in);
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt Number value to decode"));
        }
    }
}
