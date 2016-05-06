package com.fasterxml.jackson.dataformat.avro;

public class OptionalEnumTest extends AvroTestBase
{
    protected enum Gender { M, F; } 
    
    protected static class Employee {
        public Gender gender;
    }

    private final AvroMapper MAPPER = new AvroMapper();
    
    // [dataformat-avro#12]
    public void testEnumViaGeneratedSchema() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFor(Employee.class);
        Employee input = new Employee();
        input.gender = Gender.F;
    	
        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);

        // Since this is optional field, need two bytes; one for (union) type, one for enum index
        
        assertEquals(2, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }
}
