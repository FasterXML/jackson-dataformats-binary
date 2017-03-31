package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;
import java.util.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeneratorDeepNestingTest extends CBORTestBase
{
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ObjectMapper MAPPER = cborMapper();

    // for [dataformats-binary#62]
    @SuppressWarnings("unchecked")
    public void testDeeplyNestedMap() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = MAPPER.getFactory().createGenerator(out);
        _writeNestedMap(gen, 23);
        gen.close();
        byte[] encoded = out.toByteArray();
        Map<String,Object> result = (Map<String,Object>) MAPPER.readValue(encoded, Map.class);
        _verifyNestedMap(result, 23);
    }

    private void _writeNestedMap(JsonGenerator gen, int levelsLeft) throws Exception
    {
        if (levelsLeft == 0) {
            gen.writeStartObject();
            gen.writeEndObject();
            return;
        }

        // exercise different kinds of write methods...
        switch (levelsLeft % 3) {
        case 0:
            gen.writeStartObject();
            break;
        case 1:
            gen.writeStartObject(1);
            break;
        default:
            gen.writeStartObject(gen); // bogus "current" object
            break;
        }
        gen.writeFieldName("level"+levelsLeft);
        _writeNestedMap(gen, levelsLeft-1);
        gen.writeEndObject();
    }

    @SuppressWarnings("unchecked")
    private void _verifyNestedMap(Map<String,?> map, int level) {
        if (level == 0) {
            assertEquals(0, map.size());
        } else {
            assertEquals(1, map.size());
            assertEquals("level"+level, map.keySet().iterator().next());
            _verifyNestedMap((Map<String,?>) map.values().iterator().next(), level-1);
        }
    }

    public void testDeeplyNestedArray() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = MAPPER.getFactory().createGenerator(out);
        _writeNestedArray(gen, 23);
        gen.close();
        byte[] encoded = out.toByteArray();
        List<?> result = (List<?>) MAPPER.readValue(encoded, List.class);
        _verifyNesteArray(result, 23);
    }

    private void _writeNestedArray(JsonGenerator gen, int levelsLeft) throws Exception
    {
        if (levelsLeft == 0) {
            gen.writeStartArray();
            gen.writeEndArray();
            return;
        }
        // exercise different kinds of write methods...
        switch (levelsLeft % 2) {
        case 0:
            gen.writeStartArray();
            break;
        default:
            gen.writeStartArray(2);
            break;
        }
        gen.writeNumber(levelsLeft);
        _writeNestedArray(gen, levelsLeft-1);
        gen.writeEndArray();
    }

    private void _verifyNesteArray(List<?> list, int level) {
        if (level == 0) {
            assertEquals(0, list.size());
        } else {
            assertEquals(2,list.size());
            assertEquals(Integer.valueOf(level), list.get(0));
            _verifyNesteArray((List<?>) list.get(1), level-1);
        }
    }
}
