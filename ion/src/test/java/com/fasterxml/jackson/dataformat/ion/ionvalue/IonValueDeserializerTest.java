package com.fasterxml.jackson.dataformat.ion.ionvalue;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.util.AccessPattern;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE;

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
    private static final IonValueMapper ION_VALUE_MAPPER = new IonValueMapper(SYSTEM, SNAKE_CASE);

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
        IonValueData source = new IonValueData();
        source.put("a", null); // Serialized to IonNull, deserialized back to java null
        source.put("b", ion("null.bool"));
        source.put("c", ion("null.int"));
        source.put("d", ion("null.float"));
        source.put("e", ion("null.decimal"));
        source.put("f", ion("null.timestamp"));
        source.put("g", ion("null.string"));
        source.put("h", ion("null.symbol"));
        source.put("i", ion("null.blob"));
        source.put("j", ion("null.clob"));
        source.put("k", ion("null.struct"));
        source.put("l", ion("null.list"));
        source.put("m", ion("null.sexp"));

        IonValue data = ION_VALUE_MAPPER.writeValueAsIonValue(source);
        IonValueData result = ION_VALUE_MAPPER.readValue(data, IonValueData.class);

        assertEquals(source, result);
    }

    @Test
    public void shouldBeAbleToDeserializeIonNullToJavaNull() throws Exception {
        IonValueData source = new IonValueData();
        source.put("a", null);
        source.put("b", ion("null"));
        source.put("c", ion("null.null"));

        IonValue data = ION_VALUE_MAPPER.writeValueAsIonValue(source);
        IonValueData result = ION_VALUE_MAPPER.readValue(data, IonValueData.class);

        IonValueData expected = new IonValueData();
        expected.put("a", null);
        expected.put("b", null);
        expected.put("c", null);

        assertEquals(expected, result);
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
    public void shouldOverrideNullAccessPatternToBeDynamic() {
        IonValueDeserializer deserializer = new IonValueDeserializer();
        assertEquals(AccessPattern.DYNAMIC, deserializer.getNullAccessPattern());
    }

    private static IonValue ion(String value) {
        return SYSTEM.singleValue(value);
    }
}
