package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroDefault;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil;

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
    public void testUnionBooleanDefault() throws Exception {
        Schema apacheSchema = ApacheAvroInteropUtil.getApacheSchema(RecordWithDefaults.class);
        Schema jacksonSchema = ApacheAvroInteropUtil.getJacksonSchema(RecordWithDefaults.class);
        //
        assertThat(jacksonSchema.getField("booleanField").defaultVal()).isEqualTo(apacheSchema.getField("booleanField").defaultVal());
    }

    @Test
    public void testUnionIntegerDefault() throws Exception {
        Schema apacheSchema = ApacheAvroInteropUtil.getApacheSchema(RecordWithDefaults.class);
        Schema jacksonSchema = ApacheAvroInteropUtil.getJacksonSchema(RecordWithDefaults.class);
        //
        assertThat(jacksonSchema.getField("intField").defaultVal()).isEqualTo(apacheSchema.getField("intField").defaultVal());
    }

    @Test
    public void testUnionStringDefault() throws Exception {
        Schema apacheSchema = ApacheAvroInteropUtil.getApacheSchema(RecordWithDefaults.class);
        Schema jacksonSchema = ApacheAvroInteropUtil.getJacksonSchema(RecordWithDefaults.class);
        //
        assertThat(jacksonSchema.getField("stringField").defaultVal()).isEqualTo(apacheSchema.getField("stringField").defaultVal());
    }
}
