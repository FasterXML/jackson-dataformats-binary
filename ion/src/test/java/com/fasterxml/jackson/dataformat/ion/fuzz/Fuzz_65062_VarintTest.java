package com.fasterxml.jackson.dataformat.ion.fuzz;

import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
