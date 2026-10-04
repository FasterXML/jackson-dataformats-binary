package tools.jackson.dataformat.avro.schemaev;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Skipping a Map-valued field (one not in reader schema) must skip
// map keys as well as values
public class MapSkipEvolutionTest extends AvroTestBase
{
    static class Point {
        public int x, y;

        protected Point() { }
        public Point(int x0, int y0) {
            x = x0;
            y = y0;
        }
    }

    static class ScalarMapWriter {
        public Map<String, Integer> map;
        public int after;
    }

    static class StructMapWriter {
        public Map<String, Point> map;
        public int after;
    }

    static class Reader {
        public int after;
    }

    private final static String SCALAR_MAP_WRITER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'map','type':{'type':'map','values':'int'}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String STRUCT_MAP_WRITER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'map','type':{'type':'map','values':"
            + "{'type':'record','name':'Point','fields':["
            + "{'name':'x','type':'int'},{'name':'y','type':'int'}]}}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String READER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'after','type':'int'}"
            + "]}");

    private final AvroMapper NATIVE_MAPPER = new AvroMapper(AvroFactory.builderWithNativeDecoder().build());
    private final AvroMapper APACHE_MAPPER = new AvroMapper(AvroFactory.builderWithApacheDecoder().build());

    @Test
    public void testSkipScalarValuedMap() throws Exception {
        ScalarMapWriter input = new ScalarMapWriter();
        input.map = new LinkedHashMap<>();
        input.map.put("key1", 1);
        input.map.put("key2", 2);
        input.after = 7;
        _testSkipMap(SCALAR_MAP_WRITER_SCHEMA_JSON, input);
    }

    @Test
    public void testSkipStructValuedMap() throws Exception {
        StructMapWriter input = new StructMapWriter();
        input.map = new LinkedHashMap<>();
        input.map.put("key1", new Point(1, 2));
        input.map.put("key2", new Point(3, 4));
        input.after = 7;
        _testSkipMap(STRUCT_MAP_WRITER_SCHEMA_JSON, input);
    }

    // Maps written by Apache Avro's blocking encoder are split into multiple blocks,
    // some with negative count and byte size (skipped as bytes), others not
    @Test
    public void testSkipBlockedScalarValuedMap() throws Exception {
        Schema schema = new Schema.Parser().parse(SCALAR_MAP_WRITER_SCHEMA_JSON);
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < 40; ++i) {
            map.put("key" + i, i);
        }
        _testSkipBlockedMap(schema, map);
    }

    @Test
    public void testSkipBlockedStructValuedMap() throws Exception {
        Schema schema = new Schema.Parser().parse(STRUCT_MAP_WRITER_SCHEMA_JSON);
        Schema pointSchema = schema.getField("map").schema().getValueType();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < 40; ++i) {
            GenericRecord point = new GenericData.Record(pointSchema);
            point.put("x", i);
            point.put("y", -i);
            map.put("key" + i, point);
        }
        _testSkipBlockedMap(schema, map);
    }

    private void _testSkipMap(String writerSchemaJson, Object input) throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            byte[] avro = mapper.writer(mapper.schemaFrom(writerSchemaJson))
                    .writeValueAsBytes(input);
            _verifySkip(mapper, writerSchemaJson, avro);
        }
    }

    private void _testSkipBlockedMap(Schema schema, Map<String, Object> map) throws Exception
    {
        GenericRecord record = new GenericData.Record(schema);
        record.put("map", map);
        record.put("after", 7);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BinaryEncoder enc = new EncoderFactory().configureBlockSize(64)
                .blockingBinaryEncoder(bytes, null);
        new GenericDatumWriter<GenericRecord>(schema).write(record, enc);
        enc.flush();
        byte[] avro = bytes.toByteArray();
        // sanity check: first block must have negative count (followed by byte size)
        assertTrue(DecoderFactory.get().binaryDecoder(avro, null).readLong() < 0L);

        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            _verifySkip(mapper, schema.toString(), avro);
        }
    }

    private void _verifySkip(AvroMapper mapper, String writerSchemaJson, byte[] avro)
        throws Exception
    {
        final AvroSchema writerSchema = mapper.schemaFrom(writerSchemaJson);
        final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
        Reader result = mapper.readerFor(Reader.class)
                .with(writerSchema.withReaderSchema(readerSchema))
                .readValue(avro);
        assertEquals(7, result.after);
    }
}
