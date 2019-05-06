package com.fasterxml.jackson.dataformat.avro.schemaev;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

import java.io.ByteArrayInputStream;
import java.util.List;

public class ErrorReproduceTest extends AvroTestBase {

    static String SCHEMA_V1 = aposToQuotes("{\n" +
            "'type' : 'record',\n" +
            "'name': 'V1',\n" +
            "'namespace': 'test',\n" +
            "'doc': 'simple schema testing compatibility',\n" +
            "'fields': [\n" +
            "{ 'name': 'name', 'type': 'string', 'doc': 'object name' }\n" +
            "]}");

    static String SCHEMA_V2 = aposToQuotes("{\n" +
            "'type' : 'record',\n" +
            "'name': 'V1',\n" +
            "'namespace': 'test',\n" +
            "'doc': 'simple schema testing compatibility',\n" +
            "'fields': [\n" +
            "{ 'name': 'name', 'type': 'string', 'doc': 'object name' },\n" +
            "{ 'name': 'label', 'type': [ 'null', { 'type' : 'array', 'items' : 'string' } ] }\n" +
            "]}");

    static class DataV1 {
        public String name;

        protected DataV1() {
        }

        public DataV1(String name) {
            this.name = name;
        }
    }

    static class DataV2 {
        public String name;
        public List<String> label;

        public DataV2(String name, List<String> label) {
            this.name = name;
            this.label = label;
        }
    }

    private final ObjectMapper MAPPER = getMapper()
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public void testShouldDeserialize() throws Exception {
        final AvroSchema srcSchema = getMapper().schemaFrom(SCHEMA_V2);
        final AvroSchema dstSchema = getMapper().schemaFrom(SCHEMA_V1);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        // receive event with new schema
        DataV2 event = new DataV2("test", null);
        byte[] bytes = getMapper().writer(getMapper().schemaFrom(SCHEMA_V2)).writeValueAsBytes(event);
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

        //now I want to deserialize
        DataV1 v1 = MAPPER
                .readerFor(DataV1.class)
                .with(xlate)
                .readValue(stream);
    }
}
