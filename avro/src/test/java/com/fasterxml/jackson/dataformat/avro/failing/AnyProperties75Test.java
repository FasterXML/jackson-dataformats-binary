package com.fasterxml.jackson.dataformat.avro.failing;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

// for [dataformats-binary#75] -- not legit, probably,
// but included for now to see if there's something we
// could do
public class AnyProperties75Test extends AvroTestBase
{
    static class Pojo75
    {
        final Map<String, Object> params = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> any() {
            return this.params;
        }
        @JsonAnySetter
        public void set(String name, Object value) {
            params.put(name, value);
        }
        public Object get(String name) {
            return params.get(name);
        }
    }

    private final AvroMapper MAPPER = getMapper();

    public void testReadWriteIntSequence() throws Exception
    {
        final String SCHEMA_JSON = aposToQuotes("{\n"
                + "    'type': 'record',\n"
                + "    'name': 'Pojo75',\n"
                + "    'fields': [\n"
                + "        {'name': 'foo', 'type':'string' },\n"
                + "        {'name': 'bar', 'type':'string' }\n"
                + "    ]\n"
                + "}");

        Pojo75 input = new Pojo75();
        input.set("foo", "a");
        input.set("bar", "b");
        AvroSchema schema = MAPPER.schemaFrom(SCHEMA_JSON);
        byte[] avro = MAPPER.writer(schema)
                .writeValueAsBytes(input);
        Pojo75 result = MAPPER.readerFor(Pojo75.class)
                .with(schema)
                .readValue(avro);
        assertNotNull(result.params);
        assertEquals(2, result.params.size());
        assertEquals("a", result.params.get("foo"));
        assertEquals("b", result.params.get("bar"));
    }
}
