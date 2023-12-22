package com.fasterxml.jackson.dataformat.ion.fuzz;

import java.io.InputStream;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.ion.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

// https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=65062
public class Fuzz_65062_VarintTest
{
    @Test
    public void testFuzz65062_Varint() throws Exception {
       IonObjectMapper mapper = IonObjectMapper.builder().build();
       try (InputStream in = getClass().getResourceAsStream("/data/fuzz-65062.ion")) {
           try (JsonParser p = mapper.createParser(in)) {
               assertEquals(JsonToken.START_ARRAY, p.nextToken());
               assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
               assertEquals(NumberType.BIG_DECIMAL, p.getNumberType());
               p.getDecimalValue();
           }
           fail("Should not pass (invalid content)");
       } catch (StreamReadException e) {
           assertThat(e.getMessage(), Matchers.containsString("Corrupt Number value to decode"));
       }
    }
}
