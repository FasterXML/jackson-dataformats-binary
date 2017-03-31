package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroEncodeTest extends InteropTestBase {

    @Data
    static class Wrapper {

        private double precedingValue = 0.18273465;

        @AvroEncode(using = ApacheImplEncoding.class)
        private CustomComponent component;

        private int followingValue = 3456;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class CustomComponent {

        private int intValue;

        private byte byteValue;

        private short shortValue;

        private String stringValue;

        @Nullable
        private Map<String, ArrayList<Integer>> mapValue;

        @Nullable
        private CustomComponent nestedRecordValue;

        @Nullable
        private Double doubleValue;

        @Nullable
        private Long longValue;

    }

    public static class ApacheImplEncoding extends CustomEncoding<CustomComponent> {

        public ApacheImplEncoding() {
            schema = ApacheAvroInteropUtil.getJacksonSchema(CustomComponent.class);
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            ReflectData.get().createDatumWriter(schema).write(datum, out);
        }

        @Override
        protected CustomComponent read(Object reuse, Decoder in) throws IOException {
            return (CustomComponent) ReflectData.get().createDatumReader(schema).read(null, in);
        }

    }

    protected Wrapper wrapper;

    protected Wrapper result;

    @Before
    public void setup() throws IOException {
        wrapper = new Wrapper();
        //
        wrapper.setComponent(new CustomComponent());
        wrapper.getComponent().setByteValue((byte) 126);
        wrapper.getComponent().setIntValue(897125364);
        wrapper.getComponent().setShortValue((short) -7614);
        wrapper.getComponent().setMapValue(new HashMap<String, ArrayList<Integer>>());
        wrapper.getComponent().getMapValue().put("birds", new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6)));
        wrapper.getComponent().getMapValue().put("cats", new ArrayList<Integer>());
        wrapper.getComponent().getMapValue().put("dogs", new ArrayList<>(Arrays.asList(-1234, 56, 6767, 54134, 57, 86)));
        wrapper.getComponent().setStringValue("Hello World!");
        //
        wrapper.getComponent().setNestedRecordValue(new CustomComponent());
        wrapper.getComponent().getNestedRecordValue().setByteValue((byte) 42);
        wrapper.getComponent().getNestedRecordValue().setIntValue(9557748);
        wrapper.getComponent().getNestedRecordValue().setShortValue((short) -1542);
        wrapper.getComponent().getNestedRecordValue().setDoubleValue(Double.POSITIVE_INFINITY);
        wrapper.getComponent().getNestedRecordValue().setLongValue(Long.MAX_VALUE);
        wrapper.getComponent().getNestedRecordValue().setStringValue("Nested Hello World!");
        //
        result = roundTrip(wrapper);
    }

    @Test
    public void testByteValue() {
        assertThat(result.getComponent().getByteValue()).isEqualTo(wrapper.getComponent().getByteValue());
    }

    @Test
    public void testShortValue() {
        assertThat(result.getComponent().getShortValue()).isEqualTo(wrapper.getComponent().getShortValue());
    }

    @Test
    public void testStringValue() {
        assertThat(result.getComponent().getStringValue()).isEqualTo(wrapper.getComponent().getStringValue());
    }

    @Test
    public void testDoubleValue() {
        assertThat(result.getComponent().getDoubleValue()).isEqualTo(wrapper.getComponent().getDoubleValue());
    }

    @Test
    public void testLongValue() {
        assertThat(result.getComponent().getLongValue()).isEqualTo(wrapper.getComponent().getLongValue());
    }

    @Test
    public void testIntegerValue() {
        assertThat(result.getComponent().getIntValue()).isEqualTo(wrapper.getComponent().getIntValue());
    }

}
