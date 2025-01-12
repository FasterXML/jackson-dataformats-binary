package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class TestGeneratorBufferRecycle extends BaseTestForSmile
{
    @Test
    public void testMaps() throws Exception
    {
        SmileFactory factory = new SmileFactory();

        Map<?,?> props1 = buildMap("", 65);
        Map<?,?> props2 = buildMap("", 1);

        writeMapAndParse(factory, props1);
        writeMapAndParse(factory, props2);
        writeMapAndParse(factory, props1);
        writeMapAndParse(factory, props2);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private static void writeMapAndParse(SmileFactory factory, Map<?,?> map) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // generate
        JsonGenerator generator = factory.createGenerator(os);
        writeMap(generator, map);
        generator.close();

        // parse
        JsonParser parser = factory.createParser(os.toByteArray());
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
            gen.writeFieldName((String) entry.getKey());
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
