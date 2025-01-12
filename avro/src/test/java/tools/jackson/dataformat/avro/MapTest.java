package tools.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

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
}
