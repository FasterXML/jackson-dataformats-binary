package tools.jackson.dataformat.ion.fuzz;

import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

// https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=65062
public class Fuzz_65062_VarintTest
{
    private final IonObjectMapper MAPPER = IonObjectMapper.builder().build();

    @Test
    public void testFuzz65062_Varint() throws Exception {
       try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65062.ion")) {
           try (JsonParser p = MAPPER.createParser(in)) {
               assertEquals(JsonToken.START_ARRAY, p.nextToken());

               while (p.nextToken() == JsonToken.VALUE_NUMBER_FLOAT) {
                   p.getDecimalValue();
               }
               assertEquals(JsonToken.END_ARRAY, p.nextToken());
           }
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           // 21-Dec-2023, tatu: Not 100% sure why we won't get Number-specific fail but:
           assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode; underlying"));
       }
    }
}
