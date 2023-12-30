package tools.jackson.dataformat.ion.fuzz;

import java.io.*;

import org.hamcrest.Matchers;
import org.junit.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

// [dataformats-binary#434]
public class Fuzz434_65268_65274_NPETest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    // Test that used to fail on "getNumberType()" for `JsonToken.VALUE_NULL`
    @Test
    public void testFuzz65268() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65268.ion")) {
           try (JsonParser p = ION_MAPPER.createParser(in)) {
               assertEquals(JsonToken.VALUE_STRING, p.nextToken());
               p.getText();
               assertNull(p.nextTextValue());
               assertEquals(JsonToken.VALUE_NULL, p.currentToken());
               assertNull(p.getNumberType());
           }
       }
    }

    @Test
    public void testFuzz65274Malformed() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65274.ion")) {
            byte[] invalid = new byte[in.available()];
            new DataInputStream(in).readFully(invalid);
            ION_MAPPER.readTree(new ByteArrayInputStream(invalid));
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
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
