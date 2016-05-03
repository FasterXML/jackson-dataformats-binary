package com.fasterxml.jackson.dataformat.smile;

import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class TestFeatures
    extends SmileTestBase
{
    /*
    /**********************************************************
    /* Helper types
    /**********************************************************
     */

    static class Bean {
        public int value;
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */
    // Let's ensure indentation doesn't break anything (should be NOP)
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
}
