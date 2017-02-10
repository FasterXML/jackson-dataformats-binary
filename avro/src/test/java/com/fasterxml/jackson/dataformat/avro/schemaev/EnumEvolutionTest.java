package com.fasterxml.jackson.dataformat.avro.schemaev;

import com.fasterxml.jackson.dataformat.avro.*;

public class EnumEvolutionTest extends AvroTestBase
{
    protected final static String ENUM_SCHEMA1_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Employee\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"gender\", \"type\": { \"type\" : \"enum\","
            +" \"name\": \"Gender\", \"symbols\": [\"M\",\"F\"] }"
            +"}\n"
            +"]}";

    protected final static String ENUM_SCHEMA2_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Employee\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"gender\", \"type\": { \"type\" : \"enum\","
            +" \"name\": \"Gender\", \"symbols\": [\"O\",\"F\",\"M\"] }"
            +"}\n"
            +"]}";
    
    protected enum Gender { M, F, O; } 
    
    protected static class Employee {
        public Gender gender;
    }

    private final AvroMapper MAPPER = new AvroMapper();
    
    public void testSimple() throws Exception
    {
        AvroSchema src = MAPPER.schemaFrom(ENUM_SCHEMA1_JSON);
        AvroSchema dst = MAPPER.schemaFrom(ENUM_SCHEMA1_JSON);

        Employee input = new Employee();
        input.gender = Gender.F;

        byte[] bytes = MAPPER.writer(src).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back with new schema
        final AvroSchema xlate = src.withReaderSchema(dst);
        Employee output = MAPPER.readerFor(Employee.class).with(xlate)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }
}
