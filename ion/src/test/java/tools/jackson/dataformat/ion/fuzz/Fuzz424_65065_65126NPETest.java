package tools.jackson.dataformat.ion.fuzz;

import java.io.ByteArrayInputStream;

import org.hamcrest.Matchers;
import org.junit.Test;

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

// [dataformats-binary#424]
public class Fuzz424_65065_65126NPETest
{
    private final IonObjectMapper MAPPER = IonObjectMapper.builder().build();

    @Test
    public void testFuzz65065() throws Exception {
       try {
           byte[] bytes = {(byte) -32, (byte) 1, (byte) 0, (byte) -22, (byte) 123, (byte) -112};
           MAPPER.readTree(new ByteArrayInputStream(bytes));
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           e.printStackTrace();
           assertThat(e.getMessage(), Matchers.containsString("Internal `IonReader` error"));
       }
    }

    @Test
    public void testFuzz65126() throws Exception {
       try {
           byte[] bytes = {(byte) 1, (byte) 0};
           MAPPER.createParser(bytes).getDecimalValue();
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Current token (null) not numeric"));
       }
    }
}
