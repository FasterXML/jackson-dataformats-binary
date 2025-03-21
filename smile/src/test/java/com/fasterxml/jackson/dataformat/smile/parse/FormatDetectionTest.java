package com.fasterxml.jackson.dataformat.smile.parse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormatDetectionTest extends BaseTestForSmile
{
    static class POJO {
        public int id;
        public String name;

        public POJO() { }
        public POJO(int id, String name)
        {
            this.id = id;
            this.name = name;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    @Test
    public void testSimple() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectReader jsonReader = mapper.readerFor(POJO.class);
        final String JSON = "{\"name\":\"Bob\", \"id\":3}";

        byte[] doc = _smileDoc(JSON, true);

        ObjectReader detecting = jsonReader.withFormatDetection(jsonReader,
                jsonReader.with(new SmileFactory()));
        POJO pojo = detecting.readValue(doc);
        assertEquals(3, pojo.id);
        assertEquals("Bob", pojo.name);

        // let's verify it also works for plain JSON...
        pojo = detecting.readValue(JSON.getBytes("UTF-8"));
        assertEquals(3, pojo.id);
        assertEquals("Bob", pojo.name);
    }
}
