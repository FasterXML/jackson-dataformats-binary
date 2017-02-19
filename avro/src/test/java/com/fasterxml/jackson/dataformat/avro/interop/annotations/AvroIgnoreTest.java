package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import org.apache.avro.reflect.AvroIgnore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class AvroIgnoreTest extends InteropTestBase {


    @Test
    public void testFieldIgnored() {

        RecordWithIgnoredField r = new RecordWithIgnoredField();
        r.ignoredField = "fail";
        r.notIgnoredField = "success";

        RecordWithIgnoredField processedR = roundTrip(r);

        assertThat(processedR, is(not(nullValue())));

        assertThat(processedR.ignoredField, is(nullValue()));

        assertThat(processedR.notIgnoredField, is(equalTo("success")));


    }

    public static class RecordWithIgnoredField {
        public RecordWithIgnoredField() {}

        @AvroIgnore
        public String ignoredField;
        public String notIgnoredField;

    }

}
