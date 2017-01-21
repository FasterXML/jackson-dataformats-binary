package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.avro.testsupport.ThrottledInputStream;

import static org.junit.Assert.assertArrayEquals;

/**
 * Tests to ensure that it is possible to read a sequence of root-level
 * values from a stream.
 */
public class RootSequenceTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    public void testReadWriteIntSequence() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("int"));
        ByteArrayOutputStream b = new ByteArrayOutputStream(1000);

        // First: write a sequence of 3 root-level ints
        
        SequenceWriter sw = MAPPER.writer(schema).writeValues(b);
        sw.write(Integer.valueOf(1));
        sw.write(Integer.valueOf(123456));
        sw.write(Integer.valueOf(-999));
        sw.close();

        byte[] bytes = b.toByteArray();
        MappingIterator<Integer> it = MAPPER.readerFor(Integer.class)
                .with(schema)
                .readValues(bytes);
        assertTrue(it.hasNextValue());
        assertEquals(Integer.valueOf(1), it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals(Integer.valueOf(123456), it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals(Integer.valueOf(-999), it.nextValue());
        assertFalse(it.hasNextValue());
        it.close();
    }

    public void testReadWriteStringSequence() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("string"));
        ByteArrayOutputStream b = new ByteArrayOutputStream(1000);

        // First: write a sequence of 3 root-level Strings
        
        SequenceWriter sw = MAPPER.writer(schema).writeValues(b);
        sw.write("foo");
        sw.write("bar");
        sw.write("abcde");
        sw.close();

        byte[] bytes = b.toByteArray();
        // should just be chars and 1-byte length for each
        assertEquals(14, bytes.length);
        MappingIterator<String> it = MAPPER.readerFor(String.class)
                .with(schema)
                .readValues(bytes);
        assertTrue(it.hasNextValue());
        assertEquals("foo", it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals("bar", it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals("abcde", it.nextValue());
        assertFalse(it.hasNextValue());
        it.close();
    }

    public void testReadWriteEmployeeSequence() throws Exception
    {
        // Just for fun, use throttled input to force boundary reads...
        _testReadWriteEmployeeSequence(29);
        _testReadWriteEmployeeSequence(7);
        _testReadWriteEmployeeSequence(1);
    }

    public void _testReadWriteEmployeeSequence(int chunkSize) throws Exception
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream(1000);
        Employee boss = new Employee("Bossman", 55, new String[] { "boss@company.com" }, null);
        Employee peon1 = new Employee("Worker#1", 24, new String[] { "worker1@company.com" }, boss);
        Employee peon2 = new Employee("Worker#2", 42, new String[] { "worker2@company.com" }, boss);

        // First: write a sequence of 3 root-level Employee Objects

        SequenceWriter sw = MAPPER.writerFor(Employee.class)
                .with(getEmployeeSchema())
                .writeValues(b);
        sw.write(boss);
        int curr = b.size();
        sw.write(peon1);
        int diff = b.size() - curr;
        if (diff == 0) {
            fail("Should have output more bytes for second entry, did not, total: "+curr);
        }
        sw.write(peon2);
        sw.close();

        byte[] bytes = b.toByteArray();

        ThrottledInputStream in = new ThrottledInputStream(bytes, chunkSize);
        // So far so good: writing seems to work. How about reading?
        MappingIterator<Employee> it = MAPPER.readerFor(Employee.class)
                .with(getEmployeeSchema())
                .readValues(in);
        assertTrue(it.hasNextValue());
        Employee boss2 = it.nextValue();
        assertEquals(boss.age, boss2.age);
        assertEquals(boss.name, boss2.name);
        assertArrayEquals(boss.emails, boss2.emails);

        assertTrue(it.hasNextValue());
        Employee worker1 = it.nextValue();
        assertEquals(peon1.age, worker1.age);
        assertEquals(peon1.name, worker1.name);
        assertArrayEquals(peon1.emails, worker1.emails);

        assertTrue(it.hasNextValue());
        Employee worker2 = it.nextValue();
        assertEquals(peon2.age, worker2.age);
        assertEquals(peon2.name, worker2.name);
        assertArrayEquals(peon2.emails, worker2.emails);

        assertFalse(it.hasNextValue());
        it.close();
        in.close();
    }
}

