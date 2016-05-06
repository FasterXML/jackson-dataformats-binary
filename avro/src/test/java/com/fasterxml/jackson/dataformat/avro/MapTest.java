package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;
import java.util.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

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
        public Map<String,String> stuff = new LinkedHashMap<String,String>();

        public void setStuff(Map<String,String> arg) {
            stuff = arg;
        }
    }

    public void testSimple() throws Exception
    {
        AvroMapper mapper = getMapper();
        AvroSchema schema = mapper.schemaFrom(MAP_SCHEMA_JSON);
        Container input = new Container();
        input.stuff.put("foo", "bar");
        input.stuff.put("a", "b");

        /* Clumsy, but turns out that failures from convenience methods may
         * get masked due to auto-close. Hence this trickery.
         */
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = mapper.getFactory().createGenerator(out);
        mapper.writer(schema).writeValue(gen, input);
        gen.close();
        byte[] bytes = out.toByteArray();
        assertNotNull(bytes);

        assertEquals(16, bytes.length); // measured to be current exp size

        // and then back. Start with streaming
        JsonParser p = mapper.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("stuff", p.getCurrentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        String n = p.nextFieldName();

        // NOTE: Avro codec does NOT retain ordering, need to accept either ordering

        if (!"a".equals(n) && !"foo".equals(n)) {
            fail("Should get 'foo' or 'a', got '"+n+"'");
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());

        n = p.nextFieldName();
        if (!"a".equals(n) && !"foo".equals(n)) {
            fail("Should get 'foo' or 'a', got '"+n+"'");
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        
        p.close();

        // and then databind
        Container output = mapper.readerFor(Container.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertNotNull(output.stuff);
        assertEquals(2, output.stuff.size());
        assertEquals("bar", output.stuff.get("foo"));
        assertEquals("b", output.stuff.get("a"));

        // Actually, also verify it can be null
        input = new Container();

        out = new ByteArrayOutputStream();
        gen = mapper.getFactory().createGenerator(out);
        mapper.writer(schema).writeValue(gen, input);
        gen.close();
        bytes = out.toByteArray();
        assertNotNull(bytes);

        assertEquals(1, bytes.length); // measured to be current exp size
    }

    public void testMapOrNull() throws Exception
    {
        AvroMapper mapper = getMapper();
        AvroSchema schema = mapper.schemaFrom(MAP_OR_NULL_SCHEMA_JSON);
        Container input = new Container();
        input.stuff = null;

        byte[] bytes =  mapper.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Container output = mapper.readerFor(Container.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertNull(output.stuff);

        // or non-empty
        input = new Container();
        input.stuff.put("x", "y");

        bytes =  mapper.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(7, bytes.length); // measured to be current exp size

        // and then back
        output = mapper.readerFor(Container.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertNotNull(output.stuff);
        assertEquals(1, output.stuff.size());
        assertEquals("y", output.stuff.get("x"));
    }
}
