package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/* 23-Aug-2017, tatu: There was some confusion on whether potential ambiguity
 *   might be problematic (compared to actual one) -- this test verifies
 *   it should not be.
 */
public class AmbiguousUnionWriteTest extends AvroTestBase
{
    protected final String SCHEMA_WITH_AMBIGUITY = aposToQuotes("{\n"
            +"'type': 'record',\n"
            +"'name': 'WithUnion',\n"
            +"'fields': [\n"
            +" {'name': 'value', 'type': [ 'string'\n"
            +"   ,{ 'type' : 'record', 'name' : 'recordA',\n"
            +       "'fields' : [ { 'name' : 'extra', 'type' : 'string' } ] }"
            +"   ,{ 'type' : 'record', 'name' : 'recordB', \n"
            +       "'fields' : [ { 'name' : 'x', 'type' : 'int' } ] }"
            +"   ]\n"
            +" }\n"
            +"]}"
            );

    static class StringWrapper {
        public Object value;

        public StringWrapper(Object v) { value = v; }
        protected StringWrapper() { }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final AvroMapper MAPPER = newMapper();

    public void testWriteNoAmbiguity() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(SCHEMA_WITH_AMBIGUITY);
        StringWrapper input = new StringWrapper("foobar");
        // 23-Aug-2017, tatu: we could trigger exception with this, however:
//        StringWrapper input = new StringWrapper(new java.util.HashMap<String,Integer>());
        byte[] b = MAPPER.writerFor(StringWrapper.class)
                .with(schema)
                .writeValueAsBytes(input);
        StringWrapper output = MAPPER.readerFor(StringWrapper.class)
                .with(schema)
                .readValue(b);
        assertEquals("foobar", output.value);
    }
}
