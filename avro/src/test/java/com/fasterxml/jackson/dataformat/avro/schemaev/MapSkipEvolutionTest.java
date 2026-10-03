package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Skipping a Map-valued field (one not in reader schema) must skip
// map keys as well as values
public class MapSkipEvolutionTest extends AvroTestBase
{
    static class Point {
        public int x, y;

        protected Point() { }
        public Point(int x0, int y0) {
            x = x0;
            y = y0;
        }
    }

    static class ScalarMapWriter {
        public Map<String, Integer> map;
        public int after;
    }

    static class StructMapWriter {
        public Map<String, Point> map;
        public int after;
    }

    static class Reader {
        public int after;
    }

    private final static String SCALAR_MAP_WRITER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'map','type':{'type':'map','values':'int'}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String STRUCT_MAP_WRITER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'map','type':{'type':'map','values':"
            + "{'type':'record','name':'Point','fields':["
            + "{'name':'x','type':'int'},{'name':'y','type':'int'}]}}},"
            + "{'name':'after','type':'int'}"
            + "]}");

    private final static String READER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':["
            + "{'name':'after','type':'int'}"
            + "]}");

    private final AvroMapper NATIVE_MAPPER = new AvroMapper(AvroFactory.builderWithNativeDecoder().build());
    private final AvroMapper APACHE_MAPPER = new AvroMapper(AvroFactory.builderWithApacheDecoder().build());

    @Test
    public void testSkipScalarValuedMap() throws Exception {
        ScalarMapWriter input = new ScalarMapWriter();
        input.map = new LinkedHashMap<>();
        input.map.put("key1", 1);
        input.map.put("key2", 2);
        input.after = 7;
        _testSkipMap(SCALAR_MAP_WRITER_SCHEMA_JSON, input);
    }

    @Test
    public void testSkipStructValuedMap() throws Exception {
        StructMapWriter input = new StructMapWriter();
        input.map = new LinkedHashMap<>();
        input.map.put("key1", new Point(1, 2));
        input.map.put("key2", new Point(3, 4));
        input.after = 7;
        _testSkipMap(STRUCT_MAP_WRITER_SCHEMA_JSON, input);
    }

    private void _testSkipMap(String writerSchemaJson, Object input) throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            final AvroSchema writerSchema = mapper.schemaFrom(writerSchemaJson);
            final AvroSchema readerSchema = mapper.schemaFrom(READER_SCHEMA_JSON);
            byte[] avro = mapper.writer(writerSchema).writeValueAsBytes(input);
            Reader result = mapper.readerFor(Reader.class)
                    .with(writerSchema.withReaderSchema(readerSchema))
                    .readValue(avro);
            assertEquals(7, result.after);
        }
    }
}
