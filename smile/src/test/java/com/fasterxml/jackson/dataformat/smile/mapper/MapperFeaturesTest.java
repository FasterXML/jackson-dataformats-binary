package com.fasterxml.jackson.dataformat.smile.mapper;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class MapperFeaturesTest extends BaseTestForSmile
{
    static class Bean {
        public int value;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */
    // Let's ensure indentation doesn't break anything (should be NOP)
    @Test
    public void testIndent() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper(new SmileFactory());
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        Bean bean = new Bean();
        bean.value = 42;

        byte[] smile = mapper.writeValueAsBytes(bean);
        Bean result = mapper.readValue(smile, 0, smile.length, Bean.class);
        assertEquals(42, result.value);
    }

    @Test
    public void testCopy() throws IOException
    {
        ObjectMapper mapper1 = smileMapper();
        ObjectMapper mapper2 = mapper1.copy();

        assertNotSame(mapper1, mapper2);
        assertNotSame(mapper1.getFactory(), mapper2.getFactory());
        assertEquals(SmileFactory.class, mapper2.getFactory().getClass());
    }
}
