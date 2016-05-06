package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.databind.*;

public class RoundtripTest extends MapTest
{
    static String SCHEMA_ISSUE_16 = aposToQuotes("{\n"+
        " 'namespace':'org.example.testsnippets',\n"+
        " 'type':'record',\n"+
        " 'name':'TestDto',\n"+
        " 'fields':[\n"+
        "    {\n"+
        "        'name':'id',\n"+
        "        'type':['string', 'null']\n"+
        "    },\n"+
        "    {\n"+
        "        'name':'name',\n"+
        "        'type':['string', 'null']\n"+
        "    }\n"+
        " ]\n"+
        "}\n");

    
    static AvroSchema ISSUE_16_SCHEMA;
    static {
        try {
            ISSUE_16_SCHEMA = new AvroMapper().schemaFrom(SCHEMA_ISSUE_16);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    static class Issue16Bean {
        public CharSequence id;
        public CharSequence name;

        public org.apache.avro.Schema getSchema() {
            return ISSUE_16_SCHEMA.getAvroSchema();
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testIssue9() throws Exception
    {
        AvroSchema jsch = getEmployeeSchema();
        ObjectMapper mapper = new ObjectMapper(new AvroFactory());
        
        ObjectWriter writ = mapper.writer(jsch);
        ObjectMapper unzip = new ObjectMapper();
        byte[] avroData = writ.writeValueAsBytes(unzip.readTree
                ("{\"name\":\"Bob\",\"age\":15,\"emails\":[]}"));
        assertNotNull(avroData);
    }

    public void testIssue16() throws Exception
    {
        ObjectMapper mapper = new AvroMapper()
            .enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        ObjectWriter writ = mapper.writer(ISSUE_16_SCHEMA);

        Issue16Bean input = new Issue16Bean();
        input.id = "123";
        input.name = "John";

        byte[] avroData = null;
        try {
            avroData = writ.writeValueAsBytes(input);
            assertNotNull(avroData);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        Issue16Bean output = mapper.reader(ISSUE_16_SCHEMA)
                .forType(Issue16Bean.class).readValue(avroData);
        assertNotNull(avroData);

        assertEquals(input.id, output.id);
        assertEquals(input.name, output.name);
    }
}
