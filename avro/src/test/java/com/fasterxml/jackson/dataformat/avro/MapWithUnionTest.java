package com.fasterxml.jackson.dataformat.avro;

import java.util.*;

public class MapWithUnionTest extends AvroTestBase
{
    protected final String MAP_WITH_UNION_SCHEMA_JSON = aposToQuotes(
            "{"
            +"'name': 'Map',\n"
            +"'type': 'map',\n"
            +"'values': [ 'string', {\n"
            + "    'type' : 'map', 'values' : 'string' \n"
            + "  }\n"
            + " ] \n"
            +"}\n");

    private final AvroMapper MAPPER = getMapper();

    public void testRecordWithMap() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(MAP_WITH_UNION_SCHEMA_JSON);
        Map<String,Object> input = new LinkedHashMap<String,Object>();
        input.put("a", "123");
        input.put("xy", "foobar");
        byte[] avro = MAPPER.writer(schema)
                .writeValueAsBytes(input);

        Map<String,Object> result = MAPPER.readerFor(Map.class)
                .with(schema)
                .readValue(avro);

        assertEquals(2, result.size());
        assertEquals("123", result.get("a"));
        assertEquals("foobar", result.get("xy"));
    }
}
