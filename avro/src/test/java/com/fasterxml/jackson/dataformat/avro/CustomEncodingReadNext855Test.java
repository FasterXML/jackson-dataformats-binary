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

// [dataformats-binary#855]: `Decoder.arrayNext()` / `mapNext()` called by a
// `CustomEncoding` must read all blocks, and consume the end marker
public class CustomEncodingReadNext855Test extends AvroTestBase
{
    // Encodings that read Map/Array values using the standard Avro `Decoder` loop

    public static class ReadIntArrayEncoding extends CustomEncoding<List<Integer>> {
        public ReadIntArrayEncoding() {
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
            List<Integer> list = new ArrayList<>();
            for (long n = in.readArrayStart(); n > 0; n = in.arrayNext()) {
                for (long i = 0; i < n; ++i) {
                    list.add(in.readInt());
                }
            }
            return list;
        }
    }

    public static class ReadIntMapEncoding extends CustomEncoding<Map<String, Integer>> {
        public ReadIntMapEncoding() {
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
            Map<String, Integer> map = new LinkedHashMap<>();
            for (long n = in.readMapStart(); n > 0; n = in.mapNext()) {
                for (long i = 0; i < n; ++i) {
                    String key = in.readString();
                    map.put(key, in.readInt());
                }
            }
            return map;
        }
    }

    // Value surrounded by other fields, to verify exactly the value is read
    @JsonPropertyOrder({ "before", "value", "after" })
    public static abstract class Wrapper {
        public int before;
        public int after;

        // not a getter, so not a property
        abstract Object value();
    }

    public static class IntArrayWrapper extends Wrapper {
        @AvroEncode(using = ReadIntArrayEncoding.class)
        public List<Integer> value;

        @Override
        Object value() { return value; }
    }

    public static class IntMapWrapper extends Wrapper {
        @AvroEncode(using = ReadIntMapEncoding.class)
        public Map<String, Integer> value;

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
    public void testReadIntArray() throws Exception {
        IntArrayWrapper w = new IntArrayWrapper();
        w.value = _intList(100);
        _testRead(w);
        w.value = Collections.emptyList();
        _testRead(w);
    }

    @Test
    public void testReadIntMap() throws Exception {
        IntMapWrapper w = new IntMapWrapper();
        w.value = _intMap(40);
        _testRead(w);
        w.value = Collections.emptyMap();
        _testRead(w);
    }

    /*
    /**********************************************************************
    /* Tests, data written in multiple blocks by Apache Avro
    /**********************************************************************
     */

    @Test
    public void testReadBlockedIntArray() throws Exception {
        List<Integer> list = _intList(100);
        _testReadBlocked(IntArrayWrapper.class, list, list);
    }

    @Test
    public void testReadBlockedIntMap() throws Exception {
        Map<String, Integer> map = _intMap(40);
        _testReadBlocked(IntMapWrapper.class, map, map);
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private List<Integer> _intList(int count) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            list.add(i * 1000);
        }
        return list;
    }

    private Map<String, Integer> _intMap(int count) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < count; ++i) {
            map.put("key" + i, i);
        }
        return map;
    }

    private void _testRead(Wrapper input) throws Exception
    {
        input.before = 3;
        input.after = 7;
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            AvroSchema schema = mapper.schemaFor(input.getClass());
            byte[] avro = mapper.writer(schema).writeValueAsBytes(input);
            _verifyRead(mapper, input.getClass(), schema, avro, input.value());
        }
    }

    private void _testReadBlocked(Class<? extends Wrapper> type, Object value,
            Object expected) throws Exception
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

            _verifyRead(mapper, type, schema, avro, expected);
        }
    }

    private void _verifyRead(AvroMapper mapper, Class<?> type,
            AvroSchema schema, byte[] avro, Object expected)
        throws Exception
    {
        Wrapper result = (Wrapper) mapper.readerFor(type).with(schema).readValue(avro);
        assertNotNull(result);
        assertEquals(3, result.before);
        assertEquals(expected, result.value());
        assertEquals(7, result.after);
    }
}
