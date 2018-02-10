package com.fasterxml.jackson.dataformat.smile.mapper;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

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
    public void testIndent() throws Exception
    {
        ObjectMapper mapper = ObjectMapper.builder(new SmileFactory())
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .build();
        Bean bean = new Bean();
        bean.value = 42;
        
        byte[] smile = mapper.writeValueAsBytes(bean);
        Bean result = mapper.readValue(smile, 0, smile.length, Bean.class);
        assertEquals(42, result.value);
    }
}
