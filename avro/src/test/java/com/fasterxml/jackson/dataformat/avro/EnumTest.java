package com.fasterxml.jackson.dataformat.avro;

public class EnumTest extends AvroTestBase
{
    // gender as Avro enum
	protected final static String ENUM_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Employee\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"gender\", \"type\": { \"type\" : \"enum\","
            +" \"name\": \"Gender\", \"symbols\": [\"M\",\"F\"] }"
            +"}\n"
            +"]}";

    // gender as Avro string
    protected final static String STRING_SCHEMA_JSON = "{"
            +" \"type\": \"record\", "
            +" \"name\": \"Employee\", "
            +" \"fields\": ["
            +" {\"name\": \"gender\", \"type\": \"string\"}"
            +"]}";

    protected enum Gender { M, F; }

    protected static class Employee {
        public Gender gender;
    }

    protected static class EmployeeStr {
        public String gender;
    }

    private final AvroMapper MAPPER = newMapper();

    public void test_avroSchemaWithEnum_fromEnumValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(ENUM_SCHEMA_JSON);
        Employee input = new Employee();
        input.gender = Gender.F;

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    public void test_avroSchemaWithEnum_fromStringValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(ENUM_SCHEMA_JSON);
        EmployeeStr input = new EmployeeStr();
        input.gender = "F";

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    public void test_avroSchemaWithString_fromEnumValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(STRING_SCHEMA_JSON);
        Employee input = new Employee();
        input.gender = Gender.F;

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        // FIXME What is expected bytes length?
//        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    // Not sure this test makes sense
    public void test_avroSchemaWithString_fromStringValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(STRING_SCHEMA_JSON);
        EmployeeStr input = new EmployeeStr();
        input.gender = "F";

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        // FIXME What is expected bytes length?
//        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

}
