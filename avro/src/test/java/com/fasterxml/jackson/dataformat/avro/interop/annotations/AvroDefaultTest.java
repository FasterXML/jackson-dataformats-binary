package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroDefault;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroDefaultTest {
    static class RecordWithDefaults {
        @AvroDefault("\"Test Field\"")
        public String stringField;
        @AvroDefault("1234")
        public Integer intField;
        @AvroDefault("true")
        public Integer booleanField;
    }

    @Test
    public void testUnionBooleanDefault() {
        Schema apacheSchema = ApacheAvroInteropUtil.getApacheSchema(RecordWithDefaults.class);
        Schema jacksonSchema = ApacheAvroInteropUtil.getJacksonSchema(RecordWithDefaults.class);
        //
        assertThat(jacksonSchema.getField("booleanField").defaultValue()).isEqualTo(apacheSchema.getField("booleanField").defaultValue());
    }

    @Test
    public void testUnionIntegerDefault() {
        Schema apacheSchema = ApacheAvroInteropUtil.getApacheSchema(RecordWithDefaults.class);
        Schema jacksonSchema = ApacheAvroInteropUtil.getJacksonSchema(RecordWithDefaults.class);
        //
        assertThat(jacksonSchema.getField("intField").defaultValue()).isEqualTo(apacheSchema.getField("intField").defaultValue());
    }

    @Test
    public void testUnionStringDefault() {
        Schema apacheSchema = ApacheAvroInteropUtil.getApacheSchema(RecordWithDefaults.class);
        Schema jacksonSchema = ApacheAvroInteropUtil.getJacksonSchema(RecordWithDefaults.class);
        //
        assertThat(jacksonSchema.getField("stringField").defaultValue()).isEqualTo(apacheSchema.getField("stringField").defaultValue());
    }
}
