package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.*;

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

    static String SCHEMA_V2_JSON_RECORD = aposToQuotes("{\n"+
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

    static String SCHEMA_V2_JSON_MAP = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'RootType',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'data',\n"+
            "      'type': { \n"+
            "        'type':'map',\n"+
            "        'name':'Payload',\n"+
            "        'values' : ['string'] \n"+
            "      },\n"+
            "      'default' : { 'a' : 'f', 'b' : 'oobar' }\n"+
            "    },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_V2_JSON_LIST = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'RootType',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'data',\n"+
            "      'type': { \n"+
            "        'type':'array',\n"+
            "        'name':'Payload',\n"+
            "        'items' : ['string'] \n"+
            "      },\n"+
            "      'default' : [ 'Fo', 'obar' ]\n"+
            "    },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ValueWithRecord {
        public int x, y;
        public Metadata data;

        protected ValueWithRecord() { }
        public ValueWithRecord(int x0, int y0) {
            x = x0;
            y = y0;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ValueWithMap {
        public int x, y;
        public Map<String,String> data;

        protected ValueWithMap() { }
        public ValueWithMap(int x0, int y0) {
            x = x0;
            y = y0;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ValueWithList {
        public int x, y;
        public List<String> data;

        protected ValueWithList() { }
        public ValueWithList(int x0, int y0) {
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

    @Test
    public void testRecordDefaults() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_V2_JSON_RECORD);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new ValueWithRecord(1, 2));

        // First: read as original; no metadata
        ValueWithRecord result = MAPPER.readerFor(ValueWithRecord.class)
                .with(srcSchema)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        // expect default:
        assertNull(result.data);

        result = MAPPER.readerFor(ValueWithRecord.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        // expect default:
        assertNotNull(result.data);
        assertEquals("misc", result.data.key);
        assertEquals("foobar", result.data.value);
    }

    @Test
    public void testMapDefaults() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_V2_JSON_MAP);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new ValueWithMap(1, 2));

        ValueWithMap result = MAPPER.readerFor(ValueWithMap.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        // expect default:
        assertNotNull(result.data);
        assertEquals(2, result.data.size());
        assertEquals("f", result.data.get("a"));
        assertEquals("oobar", result.data.get("b"));
    }

    @Test
    public void testListDefaults() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_V2_JSON_LIST);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new ValueWithList(-4, 27));

        ValueWithList result = MAPPER.readerFor(ValueWithList.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(-4, result.x);
        assertEquals(27, result.y);
        // expect default:
        assertNotNull(result.data);
        assertEquals(2, result.data.size());
        assertEquals("Fo", result.data.get(0));
        assertEquals("obar", result.data.get(1));
    }

    /*
    /**********************************************************************
    /* Tests for integer default precision (not stored as float)
    /**********************************************************************
     */

    // Verify that int default values preserve precision (not stored as float)
    @Test
    public void testIntDefaultValuePrecision() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(aposToQuotes("{"
                +"'type':'record','name':'RootType',"
                +"'fields':["
                +"  {'name':'x','type':'int'},"
                +"  {'name':'y','type':'int'}"
                +"]"
                +"}"));
        // Default value 20000001 exceeds float's 24-bit mantissa (max exact int: 16777216)
        final AvroSchema dstSchema = MAPPER.schemaFrom(aposToQuotes("{"
                +"'type':'record','name':'RootType',"
                +"'fields':["
                +"  {'name':'x','type':'int'},"
                +"  {'name':'largeInt','type':'int','default':20000001},"
                +"  {'name':'y','type':'int'}"
                +"]"
                +"}"));

        Map<String,Object> input = new LinkedHashMap<>();
        input.put("x", 1);
        input.put("y", 2);
        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(input);

        Map<String,Object> result = MAPPER.readerFor(Map.class)
                .with(srcSchema.withReaderSchema(dstSchema))
                .readValue(avro);
        assertEquals(1, result.get("x"));
        assertEquals(2, result.get("y"));
        // The critical assertion: default 20000001 must survive float truncation
        assertEquals(20000001, ((Number) result.get("largeInt")).intValue(),
                "Integer default lost precision -- likely stored as float");
    }

    // Verify that long default values preserve precision
    @Test
    public void testLongDefaultValuePrecision() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(aposToQuotes("{"
                +"'type':'record','name':'RootType',"
                +"'fields':[{'name':'x','type':'int'}]"
                +"}"));
        // Default value 9007199254740993 exceeds float AND double integer precision
        final AvroSchema dstSchema = MAPPER.schemaFrom(aposToQuotes("{"
                +"'type':'record','name':'RootType',"
                +"'fields':["
                +"  {'name':'x','type':'int'},"
                +"  {'name':'bigLong','type':'long','default':9007199254740993}"
                +"]"
                +"}"));

        Map<String,Object> input = new LinkedHashMap<>();
        input.put("x", 1);
        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(input);

        Map<String,Object> result = MAPPER.readerFor(Map.class)
                .with(srcSchema.withReaderSchema(dstSchema))
                .readValue(avro);
        assertEquals(1, result.get("x"));
        assertEquals(9007199254740993L, ((Number) result.get("bigLong")).longValue(),
                "Long default lost precision -- likely stored as float");
    }
}
