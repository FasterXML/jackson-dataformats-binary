package com.fasterxml.jackson.dataformat.ion.ionvalue;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonStruct;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.util.AccessPattern;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.ion.IonParser;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IonValueDeserializerTest {
    private static class Data<T> {
        private final Map<String, T> map = new HashMap<>();

        protected Data() { }

        @JsonAnySetter
        public void put(String key, T value) {
            map.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, T> getAllData() {
            return map;
        }

        @Override
        public int hashCode() {
            return map.hashCode();
        }

        @Override
        public String toString() {
            return map.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Data<?> other = (Data<?>) obj;
            return Objects.equals(map, other.map);
        }

    }

    static class StringData extends Data<String> {
    }

    static class IonValueData extends Data<IonValue> {
    }

    private static final IonSystem SYSTEM = IonSystemBuilder.standard().build();
    private final IonValueMapper ION_VALUE_MAPPER = new IonValueMapper(SYSTEM, SNAKE_CASE);

    @Test
    public void shouldBeAbleToDeserialize() throws Exception {
        IonValue ion = ion("{a:1, b:2, c:3}");

        IonValueData data = ION_VALUE_MAPPER.readValue(ion, IonValueData.class);

        assertEquals(3, data.getAllData().size());
        assertEquals(ion("1"), data.getAllData().get("a"));
        assertEquals(ion("2"), data.getAllData().get("b"));
        assertEquals(ion("3"), data.getAllData().get("c"));
    }

    @Test
    public void shouldBeAbleToDeserializeIncludingNullList() throws Exception {
        IonValue ion = ion("{a:1, b:2, c:null.list}");

        IonValueData data = ION_VALUE_MAPPER.readValue(ion, IonValueData.class);

        assertEquals(3, data.getAllData().size());
        assertEquals(ion("1"), data.getAllData().get("a"));
        assertEquals(ion("2"), data.getAllData().get("b"));
        assertEquals(ion("null.list"), data.getAllData().get("c"));
    }

    @Test
    public void shouldBeAbleToDeserializeNullToIonNull() throws Exception {
        String ion = "{c:null}";
        verifyNullDeserialization(ion, SYSTEM.newNull());
        ION_VALUE_MAPPER.disable(IonParser.Feature.READ_NULL_AS_IONVALUE);
        verifyNullDeserialization(ion, null);
    }

    @Test
    public void shouldBeAbleToDeserializeNullList() throws Exception {
        String ion = "{c:null.list}";
        verifyNullDeserialization(ion, SYSTEM.newNullList());
        ION_VALUE_MAPPER.disable(IonParser.Feature.READ_NULL_AS_IONVALUE);
        verifyNullDeserialization(ion, SYSTEM.newNullList());
    }

    @Test
    public void shouldBeAbleToDeserializeNullStruct() throws Exception {
        String ion = "{c:null.struct}";
        verifyNullDeserialization(ion, SYSTEM.newNullStruct());
        ION_VALUE_MAPPER.disable(IonParser.Feature.READ_NULL_AS_IONVALUE);
        verifyNullDeserialization(ion, SYSTEM.newNullStruct());
    }

    @Test
    public void shouldBeAbleToDeserializeNullSexp() throws Exception {
        String ion = "{c:null.sexp}";
        verifyNullDeserialization(ion, SYSTEM.newNullSexp());
        ION_VALUE_MAPPER.disable(IonParser.Feature.READ_NULL_AS_IONVALUE);
        verifyNullDeserialization(ion, SYSTEM.newNullSexp());
    }

    private void verifyNullDeserialization(String ionString, IonValue expected) throws Exception {

        IonValueData data = ION_VALUE_MAPPER.readValue(ionString, IonValueData.class);

        assertEquals(1, data.getAllData().size());
        assertEquals(expected, data.getAllData().get("c"));

        IonValue ion = ion(ionString);
        data = ION_VALUE_MAPPER.readValue(ion, IonValueData.class);

        assertEquals(1, data.getAllData().size());
        assertEquals(expected, data.getAllData().get("c"));
    }

    @Test
    public void shouldBeAbleToDeserializeNullValue() throws Exception {
        IonValue ion = SYSTEM.newNull();

        IonValue data = ION_VALUE_MAPPER.readValue(ion, IonValue.class);

        assertEquals(ion, data);
    }

    @Test
    public void shouldBeAbleToDeserializeAnnotatedNullStruct() throws Exception {
        IonValue ion = ion("foo::null.struct");

        IonValue data = ION_VALUE_MAPPER.readValue(ion, IonValue.class);

        assertEquals(ion, data);
        assertEquals(1, data.getTypeAnnotations().length);
        assertEquals("foo", data.getTypeAnnotations()[0]);
    }

    @Test
    public void shouldBeAbleToDeserializeAnnotatedNullList() throws Exception {
        IonValue ion = ion("foo::null.list");

        IonValue data = ION_VALUE_MAPPER.readValue(ion, IonValue.class);

        assertEquals(ion, data);
        assertEquals(1, data.getTypeAnnotations().length);
        assertEquals("foo", data.getTypeAnnotations()[0]);
    }

    @Test
    public void shouldBeAbleToSerializeAndDeserializePojo() throws Exception {
        IonValueData source = new IonValueData();
        source.put("a", ion("1"));
        source.put("c", ion("null.list"));

        IonValue data = ION_VALUE_MAPPER.writeValueAsIonValue(source);
        IonValueData result = ION_VALUE_MAPPER.readValue(data, IonValueData.class);

        assertEquals(source, result);
    }

    @Test
    public void shouldBeAbleToSerializeAndDeserializeStringData() throws Exception {
        StringData source = new StringData();
        source.put("a", "1");
        source.put("b", null);

        IonValue data = ION_VALUE_MAPPER.writeValueAsIonValue(source);
        StringData result = ION_VALUE_MAPPER.parse(data, StringData.class);
        assertEquals(source, result);
    }

    @Test
    public void shouldBeAbleToSerializeAndDeserializeStringDataAsString() throws Exception {
        StringData source = new StringData();
        source.put("a", "1");
        source.put("b", null);

        String data = ION_VALUE_MAPPER.writeValueAsString(source);
        StringData result = ION_VALUE_MAPPER.readValue(data, StringData.class);
        assertEquals(source, result);
    }

    static class MyBean {
        public IonStruct required;
        public IonStruct optional;

        MyBean(
            @JsonProperty("required") IonStruct required,
            @JsonProperty("optional") IonStruct optional
        ) {
            this.required = required;
            this.optional = optional;
        }
    }

    @Test
    public void testWithMissingProperty() throws IOException
    {
        IonSystem ionSystem = IonSystemBuilder.standard().build();
        IonObjectMapper ionObjectMapper = IonObjectMapper.builder(ionSystem)
            .addModule(new IonValueModule())
            .build();

        String input1 = "{required:{}, optional:{}}";
        MyBean deserializedBean1 = ionObjectMapper.readValue(input1, MyBean.class);
        assertEquals(ionSystem.newEmptyStruct(), deserializedBean1.required);
        assertEquals(ionSystem.newEmptyStruct(), deserializedBean1.optional);

        // This deserialization should not fail with missing property
        String input2 = "{required:{}}";
        MyBean deserializedBean2 = ionObjectMapper.readValue(input2, MyBean.class);
        assertEquals(ionSystem.newEmptyStruct(), deserializedBean2.required);
        assertNull(deserializedBean2.optional);
    }

    @Test
    public void shouldOverrideNullAccessPatternToBeDynamic() {
        IonValueDeserializer deserializer = new IonValueDeserializer();
        assertEquals(AccessPattern.DYNAMIC, deserializer.getNullAccessPattern());
    }

    private static IonValue ion(String value) {
        return SYSTEM.singleValue(value);
    }
}
