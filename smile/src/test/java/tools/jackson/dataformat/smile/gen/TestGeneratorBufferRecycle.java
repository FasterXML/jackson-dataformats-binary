package tools.jackson.dataformat.smile.gen;

import java.io.*;
import java.util.*;

import tools.jackson.core.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

/* Test based on kimchy's issue (see https://gist.github.com/853232);
 * exhibits an issue with buffer recycling.
 */
public class TestGeneratorBufferRecycle extends BaseTestForSmile
{
    public void testMaps() throws Exception
    {
        ObjectMapper mapper = newSmileMapper();

        Map<?,?> props1 = buildMap("", 65);
        Map<?,?> props2 = buildMap("", 1);

        writeMapAndParse(mapper, props1);
        writeMapAndParse(mapper, props2);
        writeMapAndParse(mapper, props1);
        writeMapAndParse(mapper, props2);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private static void writeMapAndParse(ObjectMapper mapper, Map<?,?> map) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // generate
        JsonGenerator generator = mapper.createGenerator(os);
        writeMap(generator, map);
        generator.close();

        // parse
        JsonParser parser = mapper.createParser(os.toByteArray());
        while (parser.nextToken() != null) {

        }
        parser.close();
    }

    private static Map<?,?> buildMap(String prefix, int size) {
        HashMap<String,String> props = new HashMap<String, String>();
        for (int it = 0; it < size; it++) {
            String key = prefix + "prop_" + it;
            props.put(key, "a");
        }
        return props;
    }


    // A sample utility to write a map

    public static void writeMap(JsonGenerator gen, Map<?,?> map) throws IOException {
        gen.writeStartObject();

        for (Map.Entry<?,?> entry : map.entrySet()) {
            gen.writeName((String) entry.getKey());
            Object value = entry.getValue();
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.toString());
            }
        }

        gen.writeEndObject();
    }
}
