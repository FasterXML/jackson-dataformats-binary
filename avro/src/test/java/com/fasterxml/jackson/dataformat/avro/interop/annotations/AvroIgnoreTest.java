package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import org.apache.avro.reflect.AvroIgnore;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;

import static org.junit.Assert.assertThat;

import java.io.IOException;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

public class AvroIgnoreTest extends InteropTestBase
{
    static class RecordWithIgnoredField {
        public RecordWithIgnoredField() {}

        @AvroIgnore
        public String ignoredField;
        public String notIgnoredField;
    }

    @Before
    public void setup() {
        // 2.8 doesn't generate schemas with compatible namespaces for Apache deserializer
        assumeCompatibleNsForDeser();
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
