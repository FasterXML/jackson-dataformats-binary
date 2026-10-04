package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#852]: `Decoder.skipMap()` / `skipArray()` called by
// a `CustomEncoding` must skip the whole value (and nothing more)
public class CustomEncodingSkip852Test extends AvroTestBase
{
    // Encodings that write Map/Array values normally, but skip them on read

    public static class SkipIntMapEncoding extends CustomEncoding<Map<String, Integer>> {
        public SkipIntMapEncoding() {
            schema = SchemaBuilder.map().values().intType();
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
            assertEquals(0L, in.skipMap());
            return null;
        }
    }

    public static class SkipIntArrayEncoding extends CustomEncoding<List<Integer>> {
        public SkipIntArrayEncoding() {
            schema = SchemaBuilder.array().items().intType();
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
            assertEquals(0L, in.skipArray());
            return null;
        }
    }

    // Map with Record values, to verify nested structures are skipped as well
    public static class SkipPointMapEncoding extends CustomEncoding<Map<String, int[]>> {
        public SkipPointMapEncoding() {
            schema = SchemaBuilder.map().values(SchemaBuilder.record("Point").fields()
                    .requiredInt("x").requiredInt("y").endRecord());
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            @SuppressWarnings("unchecked")
            Map<String, int[]> map = (Map<String, int[]>) datum;
            out.writeMapStart();
            out.setItemCount(map.size());
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                out.startItem();
                out.writeString(entry.getKey());
                out.writeInt(entry.getValue()[0]);
                out.writeInt(entry.getValue()[1]);
            }
            out.writeMapEnd();
        }

        @Override
        protected Map<String, int[]> read(Object reuse, Decoder in) throws IOException {
            assertEquals(0L, in.skipMap());
            return null;
        }
    }

    // Skipped value surrounded by other fields, to verify exactly the value is skipped
    @JsonPropertyOrder({ "before", "value", "after" })
    public static abstract class Wrapper {
        public int before;
        public int after;

        // not a getter, so not a property
        abstract Object value();
    }

    public static class IntMapWrapper extends Wrapper {
        @AvroEncode(using = SkipIntMapEncoding.class)
        public Map<String, Integer> value;

        @Override
        Object value() { return value; }
    }

    public static class IntArrayWrapper extends Wrapper {
        @AvroEncode(using = SkipIntArrayEncoding.class)
        public List<Integer> value;

        @Override
        Object value() { return value; }
    }

    public static class PointMapWrapper extends Wrapper {
        @AvroEncode(using = SkipPointMapEncoding.class)
        public Map<String, int[]> value;

        @Override
        Object value() { return value; }
    }

    private final AvroMapper NATIVE_MAPPER = new AvroMapper(AvroFactory.builderWithNativeDecoder().build());
    private final AvroMapper APACHE_MAPPER = new AvroMapper(AvroFactory.builderWithApacheDecoder().build());

    /*
    /**********************************************************************
    /* Tests, data written by Jackson
    /**********************************************************************
     */

    @Test
    public void testSkipIntMap() throws Exception {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("ab", 1);
        map.put("cd", 2);
        _testSkip(IntMapWrapper.class, _intMapWrapper(map));
        _testSkip(IntMapWrapper.class, _intMapWrapper(Collections.emptyMap()));
    }

    @Test
    public void testSkipIntArray() throws Exception {
        _testSkip(IntArrayWrapper.class, _intArrayWrapper(Arrays.asList(100, 200, 300)));
        _testSkip(IntArrayWrapper.class, _intArrayWrapper(Collections.emptyList()));
    }

    @Test
    public void testSkipPointMap() throws Exception {
        PointMapWrapper input = new PointMapWrapper();
        input.before = 3;
        input.value = new LinkedHashMap<>();
        input.value.put("a", new int[] { 1, 2 });
        input.value.put("b", new int[] { 3, 4 });
        input.after = 7;
        _testSkip(PointMapWrapper.class, input);
    }

    /*
    /**********************************************************************
    /* Tests, data written in multiple blocks by Apache Avro
    /**********************************************************************
     */

    @Test
    public void testSkipBlockedIntMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < 40; ++i) {
            map.put("key" + i, i);
        }
        _testSkipBlocked(IntMapWrapper.class, map);
    }

    @Test
    public void testSkipBlockedIntArray() throws Exception {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            list.add(i * 1000);
        }
        _testSkipBlocked(IntArrayWrapper.class, list);
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private IntMapWrapper _intMapWrapper(Map<String, Integer> map) {
        IntMapWrapper w = new IntMapWrapper();
        w.before = 3;
        w.value = map;
        w.after = 7;
        return w;
    }

    private IntArrayWrapper _intArrayWrapper(List<Integer> list) {
        IntArrayWrapper w = new IntArrayWrapper();
        w.before = 3;
        w.value = list;
        w.after = 7;
        return w;
    }

    private void _testSkip(Class<? extends Wrapper> type, Wrapper input) throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            AvroSchema schema = mapper.schemaFor(type);
            byte[] avro = mapper.writer(schema).writeValueAsBytes(input);
            _verifySkip(mapper, type, schema, avro);
        }
    }

    private void _testSkipBlocked(Class<? extends Wrapper> type, Object value) throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            AvroSchema schema = mapper.schemaFor(type);
            Schema avroSchema = schema.getAvroSchema();
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("before", 3);
            record.put("value", value);
            record.put("after", 7);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BinaryEncoder enc = new EncoderFactory().configureBlockSize(64)
                    .blockingBinaryEncoder(bytes, null);
            new GenericDatumWriter<GenericRecord>(avroSchema).write(record, enc);
            enc.flush();
            byte[] avro = bytes.toByteArray();
            // sanity check: must be written in multiple blocks, first one with
            // negative count (followed by byte size)
            BinaryDecoder dec = DecoderFactory.get().binaryDecoder(avro, null);
            assertEquals(3, dec.readInt());
            assertTrue(dec.readLong() < 0L);

            _verifySkip(mapper, type, schema, avro);
        }
    }

    private void _verifySkip(AvroMapper mapper, Class<? extends Wrapper> type,
            AvroSchema schema, byte[] avro)
        throws Exception
    {
        Wrapper result = mapper.readerFor(type).with(schema).readValue(avro);
        assertNotNull(result);
        assertEquals(3, result.before);
        assertNull(result.value());
        assertEquals(7, result.after);
    }
}
