package com.fasterxml.jackson.dataformat.ion.failing;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.ion.*;
import com.fasterxml.jackson.dataformat.ion.fuzz.IonFuzzTestUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

//[dataformats-binary#473]: ArrayIndexOutOfBoundsException
//https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=66131
public class IonFuzz_473_66131_AIOOBE_Test
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    @Test
    public void testFuzz66077_ArrayIndexOOBE() throws Exception {
        final byte[] doc = IonFuzzTestUtil.readResource("/data/fuzz-66131.ion");
        try (JsonParser p = ION_MAPPER.createParser(doc)) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            assertNull(p.nextToken());
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            // May or may not be the exception message to get, change as appropriate
            assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
        }
    }
}
