package tools.jackson.dataformat.avro;

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

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.SequenceWriter;

import static org.junit.jupiter.api.Assertions.*;

public class MapTest extends AvroTestBase
{
    private final static String MAP_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Container\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"stuff\", \"type\":{\n"
            +"    \"type\":\"map\", \"values\":[\"string\",\"null\"]"
            +" }}"
            +"]}"
            ;

    private final static String MAP_OR_NULL_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Container\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"stuff\", \"type\":[\n"
            +"    \"null\", { \"type\" : \"map\", \"values\":\"string\" } \n"
            +" ]}\n"
            +"]}"
            ;
    static class Container {
        public Map<String,String> stuff = new LinkedHashMap<>();

        public void setStuff(Map<String,String> arg) {
            stuff = arg;
        }
    }

    private final AvroMapper MAPPER = getMapper();

    @Test
    public void testRecordWithMap() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(MAP_SCHEMA_JSON);
        Container input = new Container();
        input.stuff.put("foo", "bar");
        input.stuff.put("a", "b");

        /* Clumsy, but turns out that failures from convenience methods may
         * get masked due to auto-close. Hence this trickery.
         */
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MAPPER.writer(schema).writeValue(out, input);
        byte[] bytes = out.toByteArray();
        assertNotNull(bytes);

        assertEquals(16, bytes.length); // measured to be current exp size

        // and then back. Start with streaming
        JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("stuff", p.currentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        String n = p.nextName();

        // NOTE: Avro codec does NOT retain ordering, need to accept either ordering

        if (!"a".equals(n) && !"foo".equals(n)) {
            fail("Should get 'foo' or 'a', got '"+n+"'");
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());

        n = p.nextName();
        if (!"a".equals(n) && !"foo".equals(n)) {
            fail("Should get 'foo' or 'a', got '"+n+"'");
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());

        p.close();

        // and then databind
        Container output = MAPPER.readerFor(Container.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertNotNull(output.stuff);
        assertEquals(2, output.stuff.size());
        assertEquals("bar", output.stuff.get("foo"));
        assertEquals("b", output.stuff.get("a"));

        // Actually, also verify it can be null
        input = new Container();

        out = new ByteArrayOutputStream();
        MAPPER.writer(schema).writeValue(out, input);
        bytes = out.toByteArray();
        assertNotNull(bytes);

        assertEquals(1, bytes.length); // measured to be current exp size
    }

    @Test
    public void testMapOrNull() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(MAP_OR_NULL_SCHEMA_JSON);
        Container input = new Container();
        input.stuff = null;

        byte[] bytes =  MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Container output = MAPPER.readerFor(Container.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertNull(output.stuff);

        // or non-empty
        input = new Container();
        input.stuff.put("x", "y");

        bytes =  MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(7, bytes.length); // measured to be current exp size

        // and then back
        output = MAPPER.readerFor(Container.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertNotNull(output.stuff);
        assertEquals(1, output.stuff.size());
        assertEquals("y", output.stuff.get("x"));
    }

    // 18-Jan-2017, tatu: It would seem reasonable to support root-level Maps too,
    //   since Records and Arrays work, but looks like there are some issues
    //   regarding them so can't yet test

    @Test
    public void testRootStringMap() throws Exception
    {
        AvroSchema schema = getStringMapSchema();
        Map<String,String> input = _map("a", "1", "b", "2");

        byte[] b = MAPPER.writer(schema).writeValueAsBytes(input);
        Map<String,String> result = MAPPER.readerFor(Map.class)
                .with(schema)
                .readValue(b);
        assertEquals(2, result.size());
        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
    }
    @Test
    public void testRootMapSequence() throws Exception
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream(1000);
        AvroSchema schema = getStringMapSchema();
        Map<String,String> input1 = _map("a", "1", "b", "2");
        Map<String,String> input2 = _map("c", "3", "d", "4");

        SequenceWriter sw = MAPPER.writerFor(Map.class)
            .with(schema)
            .writeValues(b);
        sw.write(input1);
        int curr = b.size();
        sw.write(input2);
        int diff = b.size() - curr;
        if (diff == 0) {
            fail("Should have output more bytes for second entry, did not, total: "+curr);
        }
        sw.close();

        byte[] bytes = b.toByteArray();

        assertNotNull(bytes);

        MappingIterator<Map<String,String>> it = MAPPER.readerFor(Map.class)
                .with(schema)
                .readValues(bytes);
        assertTrue(it.hasNextValue());
        assertEquals(input1, it.nextValue());

        assertTrue(it.hasNextValue());
        assertEquals(input2, it.nextValue());

        assertFalse(it.hasNextValue());
        it.close();
    }

    private Map<String,String> _map(String... stuff) {
        Map<String,String> map = new LinkedHashMap<>();
        for (int i = 0, end = stuff.length; i < end; i += 2) {
            map.put(stuff[i], stuff[i+1]);
        }
        return map;
    }

    /*
    /**********************************************************************
    /* Maps written in multiple blocks (by Apache Avro blocking encoder)
    /**********************************************************************
     */

    static class Point {
        public int x, y;
    }

    static class IntMapWrapper {
        public Map<String, Integer> map;
        public int after;
    }

    static class PointMapWrapper {
        public Map<String, Point> map;
        public int after;
    }

    private final static String INT_MAP_WRAPPER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Wrapper','fields':["
            + "{'name':'map','type':{'type':'map','values':'int'}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String POINT_MAP_WRAPPER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Wrapper','fields':["
            + "{'name':'map','type':{'type':'map','values':"
            + "{'type':'record','name':'Point','fields':["
            + "{'name':'x','type':'int'},{'name':'y','type':'int'}]}}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    // Second and later blocks of a Map must be read correctly, for Maps
    // with scalar values
    @Test
    public void testMultiBlockIntMap() throws Exception
    {
        Schema schema = new Schema.Parser().parse(INT_MAP_WRAPPER_SCHEMA_JSON);
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < 40; ++i) {
            map.put("key" + i, i);
        }
        byte[] avro = _writeBlocked(schema, map);

        for (AvroMapper mapper : _mappers()) {
            IntMapWrapper result = mapper.readerFor(IntMapWrapper.class)
                    .with(mapper.schemaFrom(INT_MAP_WRAPPER_SCHEMA_JSON))
                    .readValue(avro);
            assertEquals(map, result.map);
            assertEquals(7, result.after);
        }
    }

    // ... as well as non-scalar (Record) values
    @Test
    public void testMultiBlockRecordMap() throws Exception
    {
        Schema schema = new Schema.Parser().parse(POINT_MAP_WRAPPER_SCHEMA_JSON);
        Schema pointSchema = schema.getField("map").schema().getValueType();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < 40; ++i) {
            GenericRecord point = new GenericData.Record(pointSchema);
            point.put("x", i);
            point.put("y", -i);
            map.put("key" + i, point);
        }
        byte[] avro = _writeBlocked(schema, map);

        for (AvroMapper mapper : _mappers()) {
            PointMapWrapper result = mapper.readerFor(PointMapWrapper.class)
                    .with(mapper.schemaFrom(POINT_MAP_WRAPPER_SCHEMA_JSON))
                    .readValue(avro);
            assertEquals(40, result.map.size());
            for (int i = 0; i < 40; ++i) {
                Point p = result.map.get("key" + i);
                assertEquals(i, p.x);
                assertEquals(-i, p.y);
            }
            assertEquals(7, result.after);
        }
    }

    private static AvroMapper[] _mappers() {
        return new AvroMapper[] {
                new AvroMapper(AvroFactory.builderWithNativeDecoder().build()),
                new AvroMapper(AvroFactory.builderWithApacheDecoder().build())
        };
    }

    // Writes Record with given Map (and "after" of 7) in multiple small blocks
    private static byte[] _writeBlocked(Schema schema, Map<String, Object> map) throws Exception
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
        // sanity check: first block must have negative count (followed by byte size),
        // which (with block size of 64) means there are multiple blocks
        assertTrue(DecoderFactory.get().binaryDecoder(avro, null).readLong() < 0L);
        return avro;
    }
}
