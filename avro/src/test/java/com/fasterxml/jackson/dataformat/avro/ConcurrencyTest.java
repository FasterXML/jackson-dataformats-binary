package com.fasterxml.jackson.dataformat.avro;

import java.io.*;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;

/**
 * Simple tests for some testable aspects of concurrent usage.
 * Specifically tries to ensure that usage of multiple parsers,
 * generators, from same thread, does not cause problems with
 * reuse of certain components.
 */
public class ConcurrencyTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    private final AvroSchema EMPL_SCHEMA;

    public ConcurrencyTest() throws IOException {
        EMPL_SCHEMA = getEmployeeSchema();        
    }
    
    // Simple test that creates 2 encoders and uses them in interleaved manner.
    // This should tease out simplest problems with possible encoder reuse.
    public void testMultipleEncoders() throws Exception
    {
        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        SequenceWriter sw1 = MAPPER.writer(EMPL_SCHEMA)
                .writeValues(b1);
        SequenceWriter sw2 = MAPPER.writer(EMPL_SCHEMA)
                .writeValues(b2);

        for (int i = 0; i < 200; ++i) {
            _writeEmpl(sw1, "foo", i);
            _writeEmpl(sw2, "foo", i);
        }
        sw1.close();
        sw2.close();
        assertEquals(b1.size(), b2.size());
        // value just verified once, but since Avro format stable should remain stable
        assertEquals(6926, b1.size());
    }

    public void testMultipleDecodersBlock() throws Exception {
        _testMultipleDecoders(false);
    }

    public void testMultipleDecodersStreaming() throws Exception {
        _testMultipleDecoders(true);
    }

    private void _testMultipleDecoders(boolean useStream) throws Exception
    {
        final int ROUNDS = 40;
        // Here let's do encoding linearly, to remove coupling with other test(s)
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        SequenceWriter sw = MAPPER.writer(EMPL_SCHEMA)
                .writeValues(b);
        for (int i = 0; i < ROUNDS; ++i) {
            _writeEmpl(sw, "a", i);
        }
        sw.close();
        final byte[] b1 = b.toByteArray();

        b = new ByteArrayOutputStream();
        sw = MAPPER.writer(EMPL_SCHEMA)
                .writeValues(b);
        for (int i = 0; i < ROUNDS; ++i) {
            _writeEmpl(sw, "b", i);
        }
        sw.close();
        final byte[] b2 = b.toByteArray();

        MappingIterator<Employee> it1, it2;

        if (useStream) {
            it1 = MAPPER.readerFor(Employee.class).with(EMPL_SCHEMA)
                    .readValues(b1);
            it2 = MAPPER.readerFor(Employee.class).with(EMPL_SCHEMA)
                    .readValues(b2);
        } else {
            it1 = MAPPER.readerFor(Employee.class).with(EMPL_SCHEMA)
                    .readValues(new ByteArrayInputStream(b1));
            it2 = MAPPER.readerFor(Employee.class).with(EMPL_SCHEMA)
                    .readValues(new ByteArrayInputStream(b2));
        }

        for (int i = 0; i < 40; ++i) {
            assertTrue(it1.hasNextValue());
            assertTrue(it2.hasNextValue());
            Employee e1 = it1.nextValue();
            Employee e2 = it2.nextValue();

            assertEquals("Empl"+i+"a", e1.name);
            assertEquals("Empl"+i+"b", e2.name);
            assertEquals(10+i, e1.age);
            assertEquals(10+i, e2.age);
        }
        assertFalse(it1.hasNextValue());
        assertFalse(it2.hasNextValue());
        it1.close();
        it2.close();
    }

    private void _writeEmpl(SequenceWriter sw, String type, int index) throws IOException {
        sw.write(_empl(type, index));
    }

    private Employee _empl(String type, int index) {
        return new Employee("Empl"+index+type, 10+index, new String[] { "empl"+index+"@company.com" }, null);
    }
}
