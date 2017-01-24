package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
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

    // for [dataformats-binary#39]
    final static String MAP_CONTAINER_SCHEMA_JSON = aposToQuotes("{\n"
            +" 'namespace': 'com.salesforce.conduit.avro',\n"
            +" 'type': 'record',\n"
            +" 'name': 'MapContainer',\n"
            +" 'fields': [\n"
            +"     {'name':'props', \n"
            +"        'type' : {\n"
            +"            'type' : 'map', \n"
            +"            'values': ['null','int','long','float','double','string','boolean',{'type':'map','values':['null','int','long','float','double','string','boolean']}]\n"
            +"        }\n"
            +"     }\n"
            +" ]\n"
            +"}");
    static class MapContainer {
        public Map<String, Object> props;

        public MapContainer() {}
        public MapContainer(Map<String, Object> p) {
            props = p;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final AvroMapper MAPPER = getMapper();

    public void testRootMapWithUnion() throws Exception
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

    public void testMapContainerWithNested() throws IOException
    {
        Map<String,Object> map = new LinkedHashMap<String,Object>();
        map.put("hello", "world");
        Map<String,String> otherMap = new LinkedHashMap<String,String>();
        otherMap.put("foo", "bar");
        otherMap.put("zap", "bing");
        map.put("otherMap", otherMap);
        map.put("goodbye", "charlie");
        MapContainer event = new MapContainer(map);

        AvroSchema avroSchema = MAPPER.schemaFrom(MAP_CONTAINER_SCHEMA_JSON);
        byte[] serialized = MAPPER.writer(avroSchema).writeValueAsBytes(event);

        MapContainer deserialized = MAPPER.readerFor(MapContainer.class)
                .with(avroSchema)
                .readValue(serialized);
        assertEquals(3, deserialized.props.size());        
        assertEquals("world", deserialized.props.get("hello"));
        assertEquals("charlie", deserialized.props.get("goodbye"));
        Object ob = deserialized.props.get("otherMap");
        assertTrue(ob instanceof Map<?,?>);
        Map<?,?> m = (Map<?,?>) ob;
        assertEquals("bar", m.get("foo"));
        assertEquals("bing", m.get("zap"));
    }
}
