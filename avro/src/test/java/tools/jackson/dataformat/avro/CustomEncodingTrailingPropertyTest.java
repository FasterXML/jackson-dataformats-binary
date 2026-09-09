package tools.jackson.dataformat.avro;

import java.io.IOException;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for [dataformats-binary#777]: properties following an {@code @AvroEncode}d
 * value (record, array or map) were silently dropped, because the custom decoding left
 * the parser positioned inside the encoded value instead of on its closing token.
 */
public class CustomEncodingTrailingPropertyTest extends AvroTestBase
{
    static class Component {
        public int first;
        public int second;

        protected Component() { }
        public Component(int f, int s) { first = f; second = s; }
    }

    static class NestingComponent {
        public int outerValue;
        public Component inner;

        protected NestingComponent() { }
        public NestingComponent(int o, Component i) { outerValue = o; inner = i; }
    }

    // NOTE: encodings deliberately read/write via the raw `Encoder` / `Decoder` API
    // rather than `ReflectData`, so the test does not depend on Avro's class allow-list.
    static final Schema COMPONENT_SCHEMA = SchemaBuilder.record("Component")
            .fields()
            .requiredInt("first")
            .requiredInt("second")
            .endRecord();

    static final Schema NESTING_SCHEMA = SchemaBuilder.record("NestingComponent")
            .fields()
            .requiredInt("outerValue")
            .name("inner").type(COMPONENT_SCHEMA).noDefault()
            .endRecord();

    public static class ComponentEncoding extends CustomEncoding<Component> {
        public ComponentEncoding() {
            schema = COMPONENT_SCHEMA;
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            Component c = (Component) datum;
            out.writeInt(c.first);
            out.writeInt(c.second);
        }

        @Override
        protected Component read(Object reuse, Decoder in) throws IOException {
            return new Component(in.readInt(), in.readInt());
        }
    }

    public static class NestingEncoding extends CustomEncoding<NestingComponent> {
        public NestingEncoding() {
            schema = NESTING_SCHEMA;
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            NestingComponent c = (NestingComponent) datum;
            out.writeInt(c.outerValue);
            out.writeInt(c.inner.first);
            out.writeInt(c.inner.second);
        }

        @Override
        protected NestingComponent read(Object reuse, Decoder in) throws IOException {
            return new NestingComponent(in.readInt(),
                    new Component(in.readInt(), in.readInt()));
        }
    }

    static final Schema INT_ARRAY_SCHEMA = SchemaBuilder.array().items().intType();

    static final Schema INT_MAP_SCHEMA = SchemaBuilder.map().values().intType();

    public static class IntListEncoding extends CustomEncoding<List<Integer>> {
        public IntListEncoding() {
            schema = INT_ARRAY_SCHEMA;
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            @SuppressWarnings("unchecked")
            List<Integer> list = (List<Integer>) datum;
            out.writeArrayStart();
            out.setItemCount(list.size());
            for (Integer value : list) {
                out.startItem();
                out.writeInt(value);
            }
            out.writeArrayEnd();
        }

        @Override
        protected List<Integer> read(Object reuse, Decoder in) throws IOException {
            List<Integer> list = new ArrayList<>();
            for (long n = in.readArrayStart(); n > 0; n = in.arrayNext()) {
                for (long i = 0; i < n; ++i) {
                    list.add(in.readInt());
                }
            }
            return list;
        }
    }

    public static class IntMapEncoding extends CustomEncoding<Map<String, Integer>> {
        public IntMapEncoding() {
            schema = INT_MAP_SCHEMA;
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            @SuppressWarnings("unchecked")
            Map<String, Integer> map = (Map<String, Integer>) datum;
            out.writeMapStart();
            out.setItemCount(map.size());
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                out.startItem();
                out.writeString(entry.getKey());
                out.writeInt(entry.getValue());
            }
            out.writeMapEnd();
        }

        @Override
        protected Map<String, Integer> read(Object reuse, Decoder in) throws IOException {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (long n = in.readMapStart(); n > 0; n = in.mapNext()) {
                for (long i = 0; i < n; ++i) {
                    map.put(in.readString(), in.readInt());
                }
            }
            return map;
        }
    }

    // NOTE: property names chosen so that the encoded one sorts FIRST; trailing
    // properties are the ones that used to get dropped.
    static class Wrapper {
        @AvroEncode(using = ComponentEncoding.class)
        public Component component;

        public int trailingInt;
        public String trailingText;

        protected Wrapper() { }
        public Wrapper(Component c, int i, String t) {
            component = c;
            trailingInt = i;
            trailingText = t;
        }
    }

    static class NestingWrapper {
        @AvroEncode(using = NestingEncoding.class)
        public NestingComponent component;

        public int trailingInt;

        protected NestingWrapper() { }
        public NestingWrapper(NestingComponent c, int i) {
            component = c;
            trailingInt = i;
        }
    }

    static class ArrayWrapper {
        @AvroEncode(using = IntListEncoding.class)
        public List<Integer> values;

        public int trailingInt;
        public String trailingText;

        protected ArrayWrapper() { }
        public ArrayWrapper(List<Integer> v, int i, String t) {
            values = v;
            trailingInt = i;
            trailingText = t;
        }
    }

    static class MapWrapper {
        @AvroEncode(using = IntMapEncoding.class)
        public Map<String, Integer> values;

        public int trailingInt;

        protected MapWrapper() { }
        public MapWrapper(Map<String, Integer> v, int i) {
            values = v;
            trailingInt = i;
        }
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testPropertiesAfterCustomEncodedRecord() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(Wrapper.class);
        Wrapper input = new Wrapper(new Component(13, 42), 3456, "trailing!");

        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(input);
        Wrapper result = MAPPER.readerFor(Wrapper.class).with(schema).readValue(encoded);

        assertNotNull(result.component);
        assertEquals(13, result.component.first);
        assertEquals(42, result.component.second);
        // these two used to come back as 0 / null
        assertEquals(3456, result.trailingInt);
        assertEquals("trailing!", result.trailingText);
    }

    @Test
    public void testPropertiesAfterNestedCustomEncodedRecord() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(NestingWrapper.class);
        NestingWrapper input = new NestingWrapper(
                new NestingComponent(7, new Component(13, 42)), 3456);

        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(input);
        NestingWrapper result = MAPPER.readerFor(NestingWrapper.class).with(schema).readValue(encoded);

        assertNotNull(result.component);
        assertEquals(7, result.component.outerValue);
        assertNotNull(result.component.inner);
        assertEquals(13, result.component.inner.first);
        assertEquals(42, result.component.inner.second);
        // unwinding has to pop TWO record levels here
        assertEquals(3456, result.trailingInt);
    }

    @Test
    public void testPropertiesAfterCustomEncodedArray() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(ArrayWrapper.class);
        ArrayWrapper input = new ArrayWrapper(Arrays.asList(1, 2, 3), 3456, "trailing!");

        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(input);
        ArrayWrapper result = MAPPER.readerFor(ArrayWrapper.class).with(schema).readValue(encoded);

        assertEquals(Arrays.asList(1, 2, 3), result.values);
        assertEquals(3456, result.trailingInt);
        assertEquals("trailing!", result.trailingText);
    }

    @Test
    public void testPropertiesAfterCustomEncodedMap() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(MapWrapper.class);
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("a", 1);
        values.put("b", 2);

        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(new MapWrapper(values, 3456));
        MapWrapper result = MAPPER.readerFor(MapWrapper.class).with(schema).readValue(encoded);

        assertEquals(values, result.values);
        assertEquals(3456, result.trailingInt);
    }
}
