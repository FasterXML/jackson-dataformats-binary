package com.fasterxml.jackson.dataformat.avro;

public class SimpleParseTest extends AvroTestBase
{
    public void testSimpleUser() throws Exception
    {
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

        byte[] avro = toAvro(empl);

//        System.out.println("Bytes -> "+avro.length);

        Employee result = getMapper().readerFor(Employee.class)
                .with(getEmployeeSchema())
                .readValue(avro);
        assertEquals("Bob", result.name);
        assertEquals(39, result.age);
    }
}
