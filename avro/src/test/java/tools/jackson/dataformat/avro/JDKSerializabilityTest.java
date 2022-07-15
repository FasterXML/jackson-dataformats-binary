package tools.jackson.dataformat.avro;

import java.io.*;

public class JDKSerializabilityTest extends AvroTestBase
{
    public void testNativeMapperWithModule() throws Exception {
        _testModule(newMapper());
    }
    
    public void testApacheMapperWithModule() throws Exception {
        _testModule(newApacheMapper());
    }

    private void _testModule(AvroMapper mapper) throws Exception
    {
        // very simple validation: should still work wrt serialization
        AvroMapper unfrozenMapper = serializeAndDeserialize(mapper);

        // and then simple verification that write+read still works

        Employee empl = _simpleEmployee();
        byte[] avro = toAvro(empl, unfrozenMapper);
        final Employee result = unfrozenMapper.readerFor(Employee.class)
                .with(getEmployeeSchema())
                .readValue(avro);
        assertEquals(empl.name, result.name);

        // and also verify `rebuild()` works:
        AvroMapper copy = unfrozenMapper.rebuild().build();
        assertNotSame(unfrozenMapper, copy);
        // with 3.x, factories are immutable so they need not be unshared:
        assertSame(unfrozenMapper.tokenStreamFactory(), copy.tokenStreamFactory());

        final Employee result2 = copy.readerFor(Employee.class)
                .with(getEmployeeSchema())
                .readValue(avro);
        assertEquals(empl.name, result2.name);
    }

    private AvroMapper serializeAndDeserialize(AvroMapper mapper) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);

        outputStream.writeObject(mapper);
        byte[] serializedBytes = byteArrayOutputStream.toByteArray();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedBytes);
        ObjectInputStream inputStream = new ObjectInputStream(byteArrayInputStream);
        Object deserializedObject = inputStream.readObject();
        assertTrue("Deserialized object should be an instance of ObjectMapper",
                deserializedObject instanceof AvroMapper);
        return (AvroMapper) deserializedObject;
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
