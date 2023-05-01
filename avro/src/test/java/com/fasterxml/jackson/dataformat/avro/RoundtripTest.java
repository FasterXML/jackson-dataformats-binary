package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
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


    static AvroSchema CHARSEQ_SCHEMA;
    static {
        try {
            CHARSEQ_SCHEMA = new AvroMapper().schemaFrom(SCHEMA_ISSUE_16);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class CharSeqBean {
        public CharSequence id;
        public CharSequence name;

        public org.apache.avro.Schema getSchema() {
            return CHARSEQ_SCHEMA.getAvroSchema();
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
        ObjectMapper mapper = new AvroMapper();

        ObjectWriter writ = mapper.writer(jsch);
        ObjectMapper unzip = new ObjectMapper();
        byte[] avroData = writ.writeValueAsBytes(unzip.readTree
                ("{\"name\":\"Bob\",\"age\":15,\"emails\":[]}"));
        assertNotNull(avroData);
    }

    public void testCharSequences() throws Exception
    {
        ObjectMapper mapper = AvroMapper.builder()
            .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
            .build();
        ObjectWriter writ = mapper.writer(CHARSEQ_SCHEMA);

        CharSeqBean input = new CharSeqBean();
        input.id = "123";
        input.name = "John";

        byte[] avroData = writ.writeValueAsBytes(input);
        assertNotNull(avroData);

        CharSeqBean output = mapper.reader(CHARSEQ_SCHEMA)
                .forType(CharSeqBean.class).readValue(avroData);
        assertNotNull(avroData);

        assertEquals(input.id, output.id);
        assertEquals(input.name, output.name);
    }

    public void testCharSequencesLowStringLimit() throws Exception
    {
        AvroFactory factory = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(1).build())
                .build();
        ObjectMapper mapper = AvroMapper.builder(factory)
                .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
                .build();
        ObjectWriter writ = mapper.writer(CHARSEQ_SCHEMA);

        CharSeqBean input = new CharSeqBean();
        input.id = "123";
        input.name = "John";

        byte[] avroData = writ.writeValueAsBytes(input);
        assertNotNull(avroData);

        try {
            mapper.reader(CHARSEQ_SCHEMA)
                    .forType(CharSeqBean.class).readValue(avroData);
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException ise) {
            assertTrue("unexpected exception message: " + ise.getMessage(),
                    ise.getMessage().startsWith("String value length (3) exceeds the maximum allowed"));
        }
    }
}
