package com.fasterxml.jackson.dataformat.ion.ionvalue;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.util.AccessPattern;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

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
    private static final IonFactory ionF = IonFactory.builderForTextualWriters().ionSystem(SYSTEM).build();
    private static final IonObjectMapper ION_VALUE_MAPPER = IonObjectMapper.builder(ionF)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

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
    public void shouldBeAbleToDeserializeNullList() throws Exception {
        IonValue ion = ion("{c:null.list}");

        IonValueData data = ION_VALUE_MAPPER.readValue(ion, IonValueData.class);

        assertEquals(1, data.getAllData().size());
        assertEquals(SYSTEM.newNullList(), data.getAllData().get("c"));
    }

    @Test
    public void shouldBeAbleToDeserializeNullStruct() throws Exception {
        IonValue ion = ion("{c:null.struct}");

        IonValueData data = ION_VALUE_MAPPER.readValue(ion, IonValueData.class);

        assertEquals(1, data.getAllData().size());
        assertEquals(SYSTEM.newNullStruct(), data.getAllData().get("c"));
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
        StringData result = ION_VALUE_MAPPER.readValue(data, StringData.class);

        assertEquals(source, result);
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
