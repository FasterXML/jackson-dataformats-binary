package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

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
