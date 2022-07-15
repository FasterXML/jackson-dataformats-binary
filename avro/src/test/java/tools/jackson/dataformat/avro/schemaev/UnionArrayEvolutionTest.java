package tools.jackson.dataformat.avro.schemaev;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.AvroTestBase;

import java.util.List;

public class UnionArrayEvolutionTest extends AvroTestBase {

    static String SCHEMA_V1_ARRAY_JSON = aposToQuotes("{\n"+
            " 'namespace':'org.example.testsnippets',\n"+
            " 'type':'record',\n"+
            " 'name':'TestDto',\n"+
            " 'fields':[\n"+
            "    {\n"+
            "        'name':'id',\n"+
            "        'type':['string', 'null']\n"+
            "    }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_V2_ARRAY_JSON = aposToQuotes("{\n"+
            " 'namespace':'org.example.testsnippets',\n"+
            " 'type':'record',\n"+
            " 'name':'TestDto',\n"+
            " 'fields':[\n"+
            "    {\n"+
            "        'name':'id',\n"+
            "        'type':['string', 'null']\n"+
            "    },\n"+
            "    {\n"+
            "        'name':'names',\n"+
            "        'type':['null', { 'type' :'array', 'items' : 'string'}]\n"+
            "    }\n"+
            " ]\n"+
            "}\n");

    private final AvroMapper MAPPER = getMapper();

    static class V1{
        public String id;

        public V1(){}

        public V1(String id) {
            this.id = id;
        }
    }

    static class V2 {
        public String id;
        public List<String> names;

        public V2(String id, List<String> names) {
            this.id = id;
            this.names = names;
        }

    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testRoundtripToOlderCompatibleSchema() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_V2_ARRAY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_V1_ARRAY_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new V2("test", null));
        V1 result = MAPPER.readerFor(V1.class)
                .with(xlate)
                .readValue(avro);
        assertEquals("test", result.id);
    }
}
