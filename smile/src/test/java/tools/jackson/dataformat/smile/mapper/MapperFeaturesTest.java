package tools.jackson.dataformat.smile.mapper;

import tools.jackson.databind.*;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.databind.SmileMapper;

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
        ObjectMapper mapper = SmileMapper.builder(new SmileFactory())
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .build();
        Bean bean = new Bean();
        bean.value = 42;
        
        byte[] smile = mapper.writeValueAsBytes(bean);
        Bean result = mapper.readValue(smile, 0, smile.length, Bean.class);
        assertEquals(42, result.value);
    }
}
