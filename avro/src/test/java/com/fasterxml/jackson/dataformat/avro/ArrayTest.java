package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.avro.testsupport.LimitingInputStream;

public class ArrayTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();
    
    // Simple test for a single array
    public void testRootStringArray() throws Exception
    {
        AvroSchema schema = getStringArraySchema();
        List<String> input = Arrays.asList("foo", "bar");

        byte[] b = MAPPER.writer(schema).writeValueAsBytes(input);

        // writing's good (probably), let's read. First as List:
        List<String> result = MAPPER.readerFor(List.class)
                .with(schema)
                .readValue(b);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(input.get(0), result.get(0));
        assertEquals(input.get(1), result.get(1));

        // then as String array
        String[] array = MAPPER.readerFor(String[].class)
                .with(schema)
                .readValue(b);
        assertNotNull(array);
        assertEquals(2, array.length);
        assertEquals(input.get(0), array[0]);
        assertEquals(input.get(1), array[1]);
    }

    // And more complex: sequence of (String) arrays
    public void testStringArraySequence() throws Exception
    {
        AvroSchema schema = getStringArraySchema();
        List<String> input1 = Arrays.asList("foo", "bar",
                "...........................................................!");
        List<String> input2 = Arrays.asList("foobar");
        String[] input3 = new String[] { "a",
"Something very much longer than the first entry: and with \u00DCnicod\u00E9 -- at least "
+"2 lines full of stuff... 12235u4039680346 -346-0436 34-6 -43609 4363469 436-09",
"Last and perhaps also least!"};

        // First: write a sequence of 3 root-level Employee Objects

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        SequenceWriter sw = MAPPER.writer(schema)
                .writeValues(b);
        sw.write(input1);
        int curr = b.size();
        sw.write(input2);
        int diff = b.size() - curr;
        if (diff == 0) {
            fail("Should have output more bytes for second entry, did not, total: "+curr);
        }
        sw.write(input3);
        sw.close();

        // 18-Jan-2017, tatu: This get bit tricky just because `readValues()` doesn't
        //   quite know whether to advance cursor to START_ARRAY or not, and we must
        //   instead prepare things... and use direct bind

        JsonParser p = MAPPER.getFactory().createParser(
                LimitingInputStream.wrap(new ByteArrayInputStream(b.toByteArray()), 123));
        p.setSchema(schema);

        assertToken(JsonToken.START_ARRAY, p.nextToken());

        List<?> result1 = MAPPER.readValue(p, List.class);
        _compare(input1, result1);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        List<?> result2 = MAPPER.readValue(p, List.class);
        _compare(input2, result2);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        List<?> result3 = MAPPER.readValue(p, List.class);
        _compare(Arrays.asList(input3), result3);

        assertNull(p.nextToken());
        p.close();
    }

    // And the ultimate case of sequence of arrays of records
    public void testEmployeeArraySequence() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(EMPLOYEE_ARRAY_SCHEMA_JSON);                

        Employee boss = new Employee("Bossman", 55, new String[] { "boss@company.com" }, null);
        Employee peon1 = new Employee("Worker#1", 24, new String[] { "worker1@company.com" }, boss);
        Employee peon2 = new Employee("Worker#2", 43, new String[] { "worker2@company.com" }, boss);

        // First: write a sequence of 3 root-level Employee Objects

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        SequenceWriter sw = MAPPER.writer(schema)
                .writeValues(b);
        sw.write(new Employee[] { boss, peon1, peon2 });
        int curr = b.size();
        sw.write(new Employee[] { peon2, boss });
        int diff = b.size() - curr;
        if (diff == 0) {
            fail("Should have output more bytes for second entry, did not, total: "+curr);
        }
        sw.close();

        // 18-Jan-2017, tatu: This get bit tricky just because `readValues()` doesn't
        //   quite know whether to advance cursor to START_ARRAY or not, and we must
        //   instead prepare things... and use direct bind

        JsonParser p = MAPPER.getFactory().createParser(b.toByteArray());
        p.setSchema(schema);

        assertToken(JsonToken.START_ARRAY, p.nextToken());

        Employee[] result1 = MAPPER.readValue(p, Employee[].class);
        assertEquals(3, result1.length);
        assertEquals("Bossman", result1[0].name);
        assertEquals("Worker#2", result1[2].name);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        Employee[] result2 = MAPPER.readValue(p, Employee[].class);
        assertEquals(2, result2.length);
        assertEquals("Bossman", result2[1].name);

        assertNull(p.nextToken());
        p.close();
    }
    
    private void _compare(List<String> input, List<?> result) {
        assertEquals(input, result);
    }
}
