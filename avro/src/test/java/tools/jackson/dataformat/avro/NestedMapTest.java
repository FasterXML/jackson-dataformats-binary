package tools.jackson.dataformat.avro;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.avro.schema.AvroSchemaGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NestedMapTest extends AvroTestBase
{
    public static class Nester {
        @JsonProperty
        public Map<String,Map<String,Integer>> nested;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final AvroMapper MAPPER = getMapper();

    @Test
    public void testSerialization() throws IOException
    {
        Nester fromJson = new ObjectMapper().readValue(
                "{\"nested\": {\"map\":{\"value\":1}}}"
                , Nester.class);

        //Generate schema from class
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(Nester.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();

        //Serialize
        byte[] avroData =  MAPPER.writer(schema)
                .writeValueAsBytes(fromJson);

        //Deserialize
        Nester nester = MAPPER.readerFor(Nester.class)
                .with(schema)
                .readValue(avroData);
        int val = nester.nested.get("map").get("value");
        assertEquals(1, val);
    }
}
