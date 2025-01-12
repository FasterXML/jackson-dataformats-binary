package tools.jackson.dataformat.ion.fuzz;

import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#4432]
public class Fuzz432_65273_AssertionTest
{
    private final IonFactory factory =
        IonFactory.builderForTextualWriters().build();

    @Test
    public void testFuzz65273() throws Exception {
       try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65273.ion")) {
           try (JsonParser p = factory.createParser(ObjectReadContext.empty(), in)) {
               p.nextToken();
           }
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode; underlying"));
       }
    }
}
