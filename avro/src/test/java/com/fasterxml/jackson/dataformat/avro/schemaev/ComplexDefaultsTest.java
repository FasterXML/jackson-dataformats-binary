package com.fasterxml.jackson.dataformat.avro.schemaev;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.avro.*;

public class ComplexDefaultsTest extends AvroTestBase
{
    static String SCHEMA_V1_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'RootType',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_V2_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'RootType',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'data',\n"+
            "      'type': { \n"+
            "        'type':'record',\n"+
            "        'name':'Payload',\n"+
            "        'fields':[\n"+
            "           { 'name':'key', 'type':'string' },\n"+
            "           { 'name':'value', 'type':'string' }\n"+
            "        ]\n"+
            "      },\n"+
            "      'default' : { 'key' : 'misc', 'value' : 'foobar' }\n"+
            "    },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Value {
        public int x, y;
        public Metadata data;

        protected Value() { }
        public Value(int x0, int y0) {
            x = x0;
            y = y0;
        }
    }

    static class Metadata {
        public String key, value;
    }
    
    private final AvroMapper MAPPER = getMapper();

    /*
    /**********************************************************************
    /* Success tests, simple
    /**********************************************************************
     */

    public void testAddField() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_V2_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new Value(1, 2));

        // First: read as original; no metadata
        Value result = MAPPER.readerFor(Value.class)
                .with(srcSchema)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        // expect default:
        assertNull(result.data);
        
        result = MAPPER.readerFor(Value.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        // expect default:
        assertNotNull(result.data);
        assertEquals("misc", result.data.key);
        assertEquals("foobar", result.data.value);
    }
}
