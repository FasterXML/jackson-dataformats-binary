package com.fasterxml.jackson.dataformat.avro;

import java.io.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JDKSerializabilityTest extends AvroTestBase
{
    public void testNativeMapperWithModule() throws Exception {
        _testModule(newMapper());
    }
    
    public void testApacheMapperWithModule() throws Exception {
        _testModule(newApacheMapper());
    }

    private void _testModule(ObjectMapper mapper) throws Exception
    {
        // very simple validation: should still work wrt serialization
        ObjectMapper unfrozenMapper = serializeAndDeserialize(mapper);

        // and then simple verification that write+read still works

        Employee empl = _simpleEmployee();
        byte[] avro = toAvro(empl, unfrozenMapper);
        Employee result = unfrozenMapper.readerFor(Employee.class)
                .with(getEmployeeSchema())
                .readValue(avro);
        assertEquals(empl.name, result.name);
    }

    private ObjectMapper serializeAndDeserialize(ObjectMapper mapper) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);

        outputStream.writeObject(mapper);
        byte[] serializedBytes = byteArrayOutputStream.toByteArray();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedBytes);
        ObjectInputStream inputStream = new ObjectInputStream(byteArrayInputStream);
        Object deserializedObject = inputStream.readObject();
        assertTrue("Deserialized object should be an instance of ObjectMapper",
                deserializedObject instanceof ObjectMapper);
        return (ObjectMapper) deserializedObject;
    }

    private Employee _simpleEmployee() {
        Employee boss = new Employee();
        boss.name = "Pointy";
        boss.age = 25;
        boss.emails = new String[] { "phb@oracle.com" };
        boss.boss = null;

        Employee empl = new Employee();
        empl.name = "Bob";
        empl.age = 39;
        empl.emails = new String[] { "bob@aol.com", "Bob@gmail.com" };

        // NOTE: currently problematic (gives us a union...)
        empl.boss = boss;
        return empl;
    }
}
