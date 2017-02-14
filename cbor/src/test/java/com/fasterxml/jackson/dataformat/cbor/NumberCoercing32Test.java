package com.fasterxml.jackson.dataformat.cbor;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class NumberCoercing32Test extends CBORTestBase
{
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testPrimitiveTypeInvariance() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper(
                new CBORFactory().disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                );
        Map<String, Object> map = new HashMap<>();
        map.put("longField", 1L);
        map.put("intField", 1);
        map.put("doubleField", 1.0d);
        map.put("floatField", 1.0f);
        byte[] json = mapper.writeValueAsBytes(map);
        Map<String, Object> result = mapper.readerFor(Map.class).readValue(json);
        assertEquals(Integer.class, result.get("intField").getClass());
        assertEquals(Long.class, result.get("longField").getClass());
        assertEquals(Double.class, result.get("doubleField").getClass());
        assertEquals(Float.class, result.get("floatField").getClass());
    }
}
