package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;

import org.apache.avro.reflect.AvroIgnore;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class AvroIgnoreTest extends InteropTestBase
{
    static class RecordWithIgnoredField {
        public RecordWithIgnoredField() {}

        @AvroIgnore
        public String ignoredField;
        public String notIgnoredField;
    }

    @Test
    public void testFieldIgnored() throws IOException {
        RecordWithIgnoredField r = new RecordWithIgnoredField();
        r.ignoredField = "fail";
        r.notIgnoredField = "success";

        RecordWithIgnoredField processedR = roundTrip(r);
        assertThat(processedR, is(not(nullValue())));
        assertThat(processedR.ignoredField, is(nullValue()));
        assertThat(processedR.notIgnoredField, is(equalTo("success")));
    }
}
