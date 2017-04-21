package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.util.Map;

import org.apache.avro.Schema;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.getJacksonSchema;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;
import static com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase.type;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordWithMissingType {

    public static class WrapperOuter<T> {

        @JsonProperty(required = true)
        public T inner;
    }

    public static class WrapperInner<T> {

        @JsonProperty(required = true)
        public T holder;

    }

    public static class Holder<T> {

        @JsonProperty(required = true)
        public T value;

    }

    // Schema for WrapperOuter<WrapperInner<Holder<Double>>>, but with the namespace changed so that the POJOs can't be resolved
    public static final String SCHEMA =
        "{\n  \"type\" : \"record\",\n  \"name\" : \"WrapperOuter\",\n  \"namespace\" : \"bad-namespace\",\n"
            + "  \"fields\" : [ {\n    \"name\" : \"inner\",\n    \"type\" : {\n      \"type\" : \"record\",\n"
            + "      \"name\" : \"WrapperInner\",\n      \"fields\" : [ {\n        \"name\" : \"holder\",\n"
            + "        \"type\" : {\n          \"type\" : \"record\",\n          \"name\" : \"Holder\",\n"
            + "          \"fields\" : [ {\n            \"name\" : \"value\",\n            \"type\" : {\n"
            + "              \"type\" : \"double\",\n              \"java-class\" : \"java.lang.Double\"\n            }\n"
            + "          } ]\n        }\n      } ]\n    }\n  } ]\n}";

    @SuppressWarnings("unchecked")
    @Test
    public void testRecordWithPolymorphicKeyDeserialization() throws IOException {
        Schema schema = getJacksonSchema(type(WrapperOuter.class, type(WrapperInner.class, type(Holder.class, Double.class))));
        Holder<Double> holder = new Holder<>();
        holder.value = 10.5D;
        WrapperInner<Holder<Double>> inner = new WrapperInner<>();
        inner.holder = holder;
        WrapperOuter<WrapperInner<Holder<Double>>> outer = new WrapperOuter<>();
        outer.inner = inner;

        byte[] data = jacksonSerialize(schema, outer);

        Object result = jacksonDeserialize((new Schema.Parser()).parse(SCHEMA), Object.class, data);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<String, Map<String, Map<String, Double>>>) result).get("inner").get("holder").get("value")).isEqualTo(10.5D);
    }
}
