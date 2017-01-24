package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class Issue39Test extends AvroTestBase
{
    final static String SCHEMA_MAP_OF_MAPS_JSON = aposToQuotes("{\n"
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
    
    public void testMapOfMaps() throws IOException
    {
        Map<String,Object> map = new LinkedHashMap<String,Object>();
        map.put("hello", "world");
        map.put("goodbye", "charlie");
        Map<String,String> otherMap = new LinkedHashMap<String,String>();
        otherMap.put("foo", "bar");
        map.put("otherMap", otherMap);
        MapContainer event = new MapContainer(map);

        AvroSchema avroSchema = MAPPER.schemaFrom(SCHEMA_MAP_OF_MAPS_JSON);
        byte[] serialized = MAPPER.writer(avroSchema).writeValueAsBytes(event);

        /*
        MapContainer deserialized = MAPPER.readerFor(MapContainer.class)
                .with(avroSchema)
                .readValue(serialized);
        assertEquals(3, deserialized.props.size());        
        */

        JsonParser p = MAPPER.getFactory().createParser(serialized);
        p.setSchema(avroSchema);
        JsonToken t;
        while ((t = p.nextToken()) != null) {
            System.err.println("GOT: "+t);
        }
        System.err.println("DONE!");
    }
}
